package dugar_lms_api.modules.wordtemplates.automation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class TranslationCacheService {
    public static final String PROMPT_VERSION="hindi-v1";
    private final JdbcTemplate jdbc; private final OpenAiTranslationGateway gateway;
    private final org.springframework.transaction.support.TransactionTemplate durableCache;
    private final org.springframework.transaction.support.TransactionTemplate conversionTransaction;
    private final java.util.concurrent.Semaphore conversionSlots;
    public TranslationCacheService(JdbcTemplate jdbc,OpenAiTranslationGateway gateway) {
        this(jdbc,gateway,new org.springframework.jdbc.datasource.DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public TranslationCacheService(JdbcTemplate jdbc,OpenAiTranslationGateway gateway,org.springframework.transaction.PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.gateway=gateway;
        durableCache=new org.springframework.transaction.support.TransactionTemplate(manager);
        durableCache.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        conversionTransaction=new org.springframework.transaction.support.TransactionTemplate(manager);
        int slots=4;
        if(jdbc.getDataSource() instanceof com.zaxxer.hikari.HikariDataSource pool)slots=Math.max(1,Math.min(4,pool.getMaximumPoolSize()-1));
        conversionSlots=new java.util.concurrent.Semaphore(slots,true);
    }
    public static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public record Field(String code,String source,String type,String context) {}
    public List<Map<String,Object>> convert(UUID generation,List<Field> fields) {
        if(fields.isEmpty())return List.of();
        if(jdbc.getDataSource() instanceof com.zaxxer.hikari.HikariDataSource pool && pool.getMaximumPoolSize()<2)
            throw new IllegalArgumentException("Document AI requires at least two database connections for durable usage logging");
        try {conversionSlots.acquire();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalArgumentException("Hindi conversion interrupted");}
        try {return Objects.requireNonNull(conversionTransaction.execute(tx -> convertLocked(generation,fields)));}
        finally {conversionSlots.release();}
    }
    private List<Map<String,Object>> convertLocked(UUID generation,List<Field> fields) {
        Map<String,Field> unique=new TreeMap<>();
        fields.forEach(f -> unique.put(identity(f),f));
        // Consistent acquisition order prevents deadlocks. Transaction locks
        // coordinate all app instances, not just threads in one JVM.
        for(String key:unique.keySet())jdbc.queryForList("SELECT pg_advisory_xact_lock(?)",ByteBuffer.wrap(HexFormat.of().parseHex(key)).getLong());
        Map<String,Map<String,Object>> resolved=new HashMap<>();List<OpenAiTranslationGateway.Item> misses=new ArrayList<>();
        for(var entry:unique.entrySet()) {
            Field f=entry.getValue();
            var rows=jdbc.queryForList("""
                SELECT * FROM document_translation_cache WHERE target_language='hi' AND conversion_type=?
                    AND context=? AND source_hash=? AND prompt_version=?
                """,f.type(),f.context(),hash(f.source()),PROMPT_VERSION);
            if(!rows.isEmpty()) {
                var row=rows.getFirst();if(!f.source().equals(row.get("source_value")))throw new IllegalStateException("Translation cache hash collision");
                Map<String,Object> cached=new HashMap<>(row);cached.put("cacheHit",true);resolved.put(entry.getKey(),cached);
            } else misses.add(new OpenAiTranslationGateway.Item(entry.getKey(),f.source(),f.type(),f.context()));
        }
        long hitCount=fields.stream().filter(field -> resolved.containsKey(identity(field))).count();
        durableCache.executeWithoutResult(tx -> jdbc.update("UPDATE document_generation_audit SET cache_hits=?,cache_misses=? WHERE id=?",hitCount,fields.size()-hitCount,generation));
        for(int start=0;start<misses.size();start+=25) {
            var batch=misses.subList(start,Math.min(start+25,misses.size()));var translated=gateway.translate(generation,batch);
            // Preserve successful batches even if a later request fails. The
            // outer transaction still holds advisory locks until conversion ends.
            var stored=durableCache.execute(tx -> {
                Map<String,Map<String,Object>> committed=new HashMap<>();
                for(var item:batch) {
                var row=jdbc.queryForMap("""
                    INSERT INTO document_translation_cache(target_language,conversion_type,context,source_hash,source_value,translated_value,model,prompt_version,origin_usage_id)
                    VALUES('hi',?,?,?,?,?,?,?,?) RETURNING *
                    """,item.conversion(),item.context(),hash(item.value()),item.value(),translated.values().get(item.id()),gateway.model(),PROMPT_VERSION,translated.usageId());
                jdbc.update("INSERT INTO document_translation_history(cache_id,version,translated_value,changed_by,reason) VALUES(?,1,?,'OPENAI','Initial conversion')",row.get("id"),row.get("translated_value"));
                Map<String,Object> fresh=new HashMap<>(row);fresh.put("cacheHit",false);committed.put(item.id(),fresh);
                }
                return committed;
            });
            resolved.putAll(Objects.requireNonNull(stored));
        }
        return fields.stream().map(f -> {
            var row=resolved.get(identity(f));Map<String,Object> result=new LinkedHashMap<>();result.put("code",f.code());result.put("source",f.source());
            result.put("value",row.get("translated_value"));result.put("cacheId",row.get("id"));result.put("version",row.get("version"));
            result.put("originUsageId",row.get("origin_usage_id"));result.put("verificationStatus",row.get("verification_status"));result.put("cacheHit",row.get("cacheHit"));result.put("conversion",f.type());return result;
        }).toList();
    }
    private String identity(Field f) {return hash(DocumentJson.write(List.of("hi",f.type(),f.context(),f.source(),PROMPT_VERSION)));}
    @Transactional
    public Map<String,Object> approve(long id,int version,String text,String user,String reason) {
        var row=jdbc.queryForMap("SELECT * FROM document_translation_cache WHERE id=? FOR UPDATE",id);
        if(((Number)row.get("version")).intValue()!=version)throw new IllegalArgumentException("Translation changed; refresh preview before approval");
        OpenAiTranslationGateway.validateHindi((String)row.get("source_value"),text);
        jdbc.update("INSERT INTO document_translation_history(cache_id,version,translated_value,changed_by,reason) VALUES(?,?,?,?,?)",id,version+1,text,user,reason);
        return jdbc.queryForMap("""
            UPDATE document_translation_cache SET translated_value=?,version=version+1,verification_status='APPROVED',
                verified_by=?,verified_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? RETURNING *
            """,text,user,id);
    }
}
