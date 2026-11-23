package dugar_lms_api.modules.wordtemplates.automation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

@Service
public class AiUsageService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;
    private final BigDecimal inputRate, cachedRate, outputRate, usdInr;
    public AiUsageService(JdbcTemplate jdbc,PlatformTransactionManager manager,
            @org.springframework.beans.factory.annotation.Value("${app.document-ai.input-usd-per-million:0.25}") BigDecimal inputRate,
            @org.springframework.beans.factory.annotation.Value("${app.document-ai.cached-input-usd-per-million:0.025}") BigDecimal cachedRate,
            @org.springframework.beans.factory.annotation.Value("${app.document-ai.output-usd-per-million:2.00}") BigDecimal outputRate,
            @org.springframework.beans.factory.annotation.Value("${app.document-ai.usd-inr:0}") BigDecimal usdInr) {
        this.jdbc=jdbc;this.inputRate=inputRate;this.cachedRate=cachedRate;this.outputRate=outputRate;this.usdInr=usdInr;
        independent=new TransactionTemplate(manager);independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Map<String,Object> pricing(String model) {
        if (!"gpt-5-mini".equals(model)) throw new IllegalArgumentException("Document translation requires gpt-5-mini");
        if(inputRate.signum()<0||cachedRate.signum()<0||outputRate.signum()<0||usdInr.signum()<=0)
            throw new IllegalArgumentException("Configure valid document AI token pricing and a positive app.document-ai.usd-inr exchange rate");
        // Snapshot the applied backend rates in every request log; historical DB versions remain intact.
        return Map.of("source","BACKEND_CONFIGURATION","input_per_million",inputRate,"cached_input_per_million",cachedRate,
            "output_per_million",outputRate,"usd_inr",usdInr,"multiplier",BigDecimal.valueOf(3));
    }
    public static BigDecimal cost(long input,long cached,long output,BigDecimal inputRate,BigDecimal cachedRate,BigDecimal outputRate) {
        if(input<0||cached<0||cached>input||output<0) throw new IllegalArgumentException("Invalid provider token usage");
        return inputRate.multiply(BigDecimal.valueOf(input-cached)).add(cachedRate.multiply(BigDecimal.valueOf(cached)))
            .add(outputRate.multiply(BigDecimal.valueOf(output))).divide(BigDecimal.valueOf(1_000_000),12,RoundingMode.HALF_UP);
    }
    public Long record(UUID generation,String module,String feature,String model,Instant start,String requestId,
                       Long input,Long cached,Long output,Long total,String status,String category,Map<String,Object> rates) {
        var user=jdbc.queryForMap("SELECT user_id,username,contract_number FROM document_generation_audit WHERE id=?",generation);
        return record(new Attribution(generation,module,feature,(String)user.get("contract_number"),(Long)user.get("user_id"),(String)user.get("username")),model,start,requestId,input,cached,output,total,status,category,rates);
    }
    /** Common attribution supports future AI features with no document generation. */
    public record Attribution(UUID generationId,String module,String feature,String contractReference,Long userId,String username) {}
    public Long record(Attribution attribution,String model,Instant start,String requestId,
                       Long input,Long cached,Long output,Long total,String status,String category,Map<String,Object> rates) {
        return independent.execute(tx -> {
            BigDecimal usd=null;String loggedStatus=status,loggedCategory=category;
            try {if(input!=null&&output!=null)usd=cost(input,cached==null?0:cached,output,(BigDecimal)rates.get("input_per_million"),(BigDecimal)rates.get("cached_input_per_million"),(BigDecimal)rates.get("output_per_million"));}
            catch(IllegalArgumentException invalidUsage){loggedStatus="FAILED";loggedCategory="INVALID_PROVIDER_USAGE";}
            BigDecimal fx=(BigDecimal)rates.get("usd_inr"),multiplier=(BigDecimal)rates.get("multiplier");
            BigDecimal inr=usd==null?null:usd.multiply(fx),bill=inr==null?null:inr.multiply(multiplier);
            Long id=jdbc.queryForObject("""
                INSERT INTO ai_api_usage(provider_request_id,module,feature,generation_id,contract_reference,user_id,username,
                    requested_at,responded_at,duration_ms,model,input_tokens,cached_input_tokens,output_tokens,total_tokens,
                    status,error_category,error_message,pricing_version,applied_rates,estimated_usd,usd_inr,estimated_inr,multiplier,billable_inr)
                VALUES(?,?,?,?,?,?,?, ?,CURRENT_TIMESTAMP,?,?,?,?,?,?, ?,?,?,?,?::jsonb,?,?,?,?,?) RETURNING id
                """,Long.class,requestId,attribution.module(),attribution.feature(),attribution.generationId(),attribution.contractReference(),attribution.userId(),attribution.username(),
                Timestamp.from(start),java.time.Duration.between(start,Instant.now()).toMillis(),model,input,cached,output,total,
                loggedStatus,loggedCategory,loggedCategory,rates.get("id"),DocumentJson.write(rates),usd,fx,inr,multiplier,bill);
            if(attribution.generationId()!=null)jdbc.update("INSERT INTO document_generation_api_links(generation_id,usage_id) VALUES(?,?)",attribution.generationId(),id);
            return id;
        });
    }
}
