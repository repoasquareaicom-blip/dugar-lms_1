package dugar_lms_api.modules.wordtemplates.automation;

import dugar_lms_api.modules.wordtemplates.service.WordPlaceholderRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DocumentFieldService {
    private final JdbcTemplate jdbc;
    private final WordPlaceholderRegistry registry;
    public DocumentFieldService(JdbcTemplate jdbc, WordPlaceholderRegistry registry) { this.jdbc=jdbc; this.registry=registry; }
    public Map<String,String> aliases() {
        Map<String,String> result=new HashMap<>();
        jdbc.query("SELECT alias,canonical_code FROM document_placeholder_aliases", rs -> {
            result.put(rs.getString(1).toLowerCase(Locale.ROOT),rs.getString(2));
        });
        return result;
    }
    public String canonical(String code, Map<String,String> aliases) {
        if (registry.knownCodes("CONTRACT").contains(code)) return code;
        if (!code.matches("\\{\\{[A-Za-z][A-Za-z0-9_]*}}")) return code;
        return aliases.getOrDefault(code.substring(2,code.length()-2).toLowerCase(Locale.ROOT),code);
    }
    public Set<String> knownCodes(String dataSource) {
        Set<String> result=new HashSet<>(registry.knownCodes(dataSource));
        jdbc.query("SELECT alias FROM document_placeholder_aliases", rs -> { result.add("{{"+rs.getString(1)+"}}"); });
        return result;
    }
    public List<Map<String,Object>> configuration() {
        Map<String,Map<String,Object>> rules=new HashMap<>();
        jdbc.queryForList("SELECT * FROM document_field_rules").forEach(r -> rules.put((String)r.get("canonical_code"),r));
        var aliases=jdbc.queryForList("SELECT id,alias,canonical_code FROM document_placeholder_aliases ORDER BY LOWER(alias)");
        return registry.fields("CONTRACT").stream().map(f -> {
            Map<String,Object> row=new LinkedHashMap<>(); row.put("canonicalCode",f.placeholderCode());row.put("category",f.category());
            row.put("fieldName",f.fieldName());row.put("rule",rules.getOrDefault(f.placeholderCode(),Map.of("conversion_type","PRESERVE","context",f.category())));
            row.put("aliases",aliases.stream().filter(a -> f.placeholderCode().equals(a.get("canonical_code"))).toList());return row;
        }).toList();
    }
    @Transactional
    public void saveAlias(Long id,String alias,String canonical,String user) {
        requireCanonical(canonical);
        if (alias != null) {
            alias = alias.trim();
            if (alias.startsWith("{{") && alias.endsWith("}}")) alias = alias.substring(2, alias.length()-2).trim();
        }
        final String normalizedAlias = alias;
        if (alias==null || !alias.matches("[A-Za-z][A-Za-z0-9_]{0,149}")) throw new IllegalArgumentException("Alias must start with a letter and contain only letters, numbers and underscores");
        if (registry.knownCodes("CONTRACT").stream().anyMatch(c -> c.equalsIgnoreCase("{{"+normalizedAlias+"}}")))
            throw new IllegalArgumentException("Alias collides with a canonical placeholder");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM document_placeholder_aliases WHERE LOWER(alias)=LOWER(?) AND (?::bigint IS NULL OR id<>?))",Boolean.class,alias,id,id)))
            throw new IllegalArgumentException("Alias is already in use");
        if(id==null) jdbc.update("INSERT INTO document_placeholder_aliases(alias,canonical_code,created_by) VALUES (?,?,?)",alias,canonical,user);
        else if(jdbc.update("UPDATE document_placeholder_aliases SET alias=?,canonical_code=? WHERE id=? AND canonical_code=?",alias,canonical,id,canonical)!=1)
            throw new IllegalArgumentException("Alias not found");
    }
    public void deleteAlias(long id) { jdbc.update("DELETE FROM document_placeholder_aliases WHERE id=?",id); }
    public void saveRule(String code,String type,String context,String format,String user) {
        requireCanonical(code);
        if (type==null || !Set.of("PRESERVE","TRANSLITERATE","TRANSLATE","FORMAT").contains(type)) throw new IllegalArgumentException("Unknown conversion type");
        if (!type.equals("PRESERVE") && code.matches(".*_(NUMBER|REGISTRATION_NUMBER|PHONE|PHONE_NUMBER|MOBILE|PIN_CODE|PAN|AADHAAR|ACCOUNT_NUMBER|PARTY_CODE)}}"))
            throw new IllegalArgumentException("Identifiers must use PRESERVE conversion");
        var field=registry.fields("CONTRACT").stream().filter(f -> f.placeholderCode().equals(code)).findFirst().orElseThrow();
        if(!Set.of("PRESERVE","FORMAT").contains(type)&&field.sourceColumn()!=null) {
            var types=jdbc.queryForList("SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name=? AND column_name=?",String.class,field.sourceTable(),field.sourceColumn());
            if(types.stream().anyMatch(t -> !Set.of("character varying","character","text").contains(t)))throw new IllegalArgumentException("Numeric and date fields require PRESERVE or deterministic FORMAT conversion");
        }
        if(context==null||context.isBlank()||context.length()>150) throw new IllegalArgumentException("Field context is required (maximum 150 characters)");
        if(type.equals("FORMAT")&&!Set.of("DATE","AMOUNT_WORDS","CURRENCY").contains(String.valueOf(format))) throw new IllegalArgumentException("Choose a deterministic format");
        jdbc.update("""
            INSERT INTO document_field_rules(canonical_code,conversion_type,context,format_kind,updated_by) VALUES(?,?,?,?,?)
            ON CONFLICT(canonical_code) DO UPDATE SET conversion_type=EXCLUDED.conversion_type,context=EXCLUDED.context,
                format_kind=EXCLUDED.format_kind,updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP
            """,code,type,context,type.equals("FORMAT")?format:null,user);
    }
    public Map<String,Object> rule(String code) {
        return jdbc.queryForList("SELECT * FROM document_field_rules WHERE canonical_code=?",code).stream().findFirst()
            .orElse(Map.of("conversion_type","PRESERVE","context","general"));
    }
    private void requireCanonical(String code) {
        if(!registry.knownCodes("CONTRACT").contains(code)) throw new IllegalArgumentException("Unregistered canonical placeholder");
    }
}
