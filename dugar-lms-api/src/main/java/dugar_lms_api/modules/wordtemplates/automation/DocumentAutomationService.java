package dugar_lms_api.modules.wordtemplates.automation;

import dugar_lms_api.modules.reports.ReportAccessScope;
import dugar_lms_api.modules.wordtemplates.dto.GenerateWordDocumentRequest;
import dugar_lms_api.modules.wordtemplates.repository.*;
import dugar_lms_api.modules.wordtemplates.service.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class DocumentAutomationService {
    private final JdbcTemplate jdbc;private final DocumentFieldService fields;private final TranslationCacheService cache;
    private final WordDocumentGenerationService generation;private final WordDocumentGenerationRepository contracts;
    private final WordTemplateRepository templates;private final WordTemplateStorageService storage;private final WordDocxTemplateEngine engine;
    public DocumentAutomationService(JdbcTemplate jdbc,DocumentFieldService fields,TranslationCacheService cache,
            WordDocumentGenerationService generation,WordDocumentGenerationRepository contracts,WordTemplateRepository templates,
            WordTemplateStorageService storage,WordDocxTemplateEngine engine) {
        this.jdbc=jdbc;this.fields=fields;this.cache=cache;this.generation=generation;this.contracts=contracts;this.templates=templates;this.storage=storage;this.engine=engine;
    }
    public static String user(Authentication auth) {return auth==null?"unknown":auth.getName();}
    public static Long userId(Authentication auth) {
        if(auth!=null&&auth.getDetails() instanceof Map<?,?> details&&details.get("userId")!=null)return Long.valueOf(details.get("userId").toString());
        return null;
    }
    public Map<String,Object> accessibleContract(long id,Authentication auth) {
        if(auth==null)throw new AccessDeniedException("Authentication required");
        return contracts.findAccessibleContract(id,ReportAccessScope.from(auth)).orElseThrow(() -> new AccessDeniedException("Contract not accessible"));
    }
    public Map<String,Object> contractFieldValues(String number, Authentication auth) {
        if (auth == null) throw new AccessDeniedException("Authentication required");
        if (number == null || number.isBlank()) throw new IllegalArgumentException("Contract Number is required");
        var contract = contracts.findAccessibleContractByNumber(number.trim(), ReportAccessScope.from(auth))
            .orElseThrow(() -> new AccessDeniedException("Contract not found or not accessible"));
        return Map.of("contractNumber", contract.get("contract_number"), "values", generation.resolveContractValues(contract));
    }

    public Map<String,Object> preview(GenerateWordDocumentRequest request,String language,Authentication auth) {
        return preview(request,language,auth,null);
    }
    public Map<String,Object> preview(GenerateWordDocumentRequest request,String language,Authentication auth,UUID batchId) {
        return preview(request,language,auth,batchId,null);
    }
    public Map<String,Object> preview(GenerateWordDocumentRequest request,String language,Authentication auth,UUID batchId,Map<String,String> fixedValues) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO document_generation_audit(id,batch_id,contract_id,template_id,language_code,user_id,username,status) VALUES(?,?,?,?,?,?,?,'STARTED')",
            id,batchId,request.contractId(),request.templateId(),language,userId(auth),user(auth));
        try {
            if(!Set.of("en","hi").contains(language))throw new IllegalArgumentException("Document language is not supported by the conversion service");
            if(!"CONTRACT".equalsIgnoreCase(request.dataSource()))throw new IllegalArgumentException("Only CONTRACT generation is supported");
            var contract=accessibleContract(request.contractId(),auth);
            var template=templates.findTemplate(request.templateId()).orElseThrow(() -> new IllegalArgumentException("Template not found"));
            if(!Boolean.TRUE.equals(template.isActive()))throw new IllegalArgumentException("Template is inactive");
            if(ReminderLetterService.isReminder(template)&&fixedValues==null)throw new IllegalArgumentException("Use the Reminder Letter batch workflow and As-on Date");
            if(fixedValues!=null&&!ReminderLetterService.isReminder(template))throw new IllegalArgumentException("Fixed values require a Reminder Letter template");
            if(!language.equals(template.languageCode()))throw new IllegalArgumentException("No template in the requested language; select the matching template");
            jdbc.update("UPDATE document_generation_audit SET contract_number=?,template_name=? WHERE id=?",contract.get("contract_number"),template.templateName(),id);
            var file=templates.findTemplateFile(template.id()).orElseThrow();Path path=storage.path(file.storagePath());
            String fingerprint=TranslationCacheService.hash(HexFormat.of().formatHex(Files.readAllBytes(path)));
            var values=fixedValues!=null?fixedValues:generation.resolveContractValues(contract);var aliases=fields.aliases();
            Map<String,String> tokenCodes=new LinkedHashMap<>();Map<String,Map<String,Object>> converted=new LinkedHashMap<>();
            List<TranslationCacheService.Field> toConvert=new ArrayList<>();
            for(String token:engine.extractPlaceholders(path)) {
                String canonical=values.containsKey(token)?token:fields.canonical(token,aliases);if(!values.containsKey(canonical))throw new IllegalArgumentException("Unknown placeholder: "+token);
                tokenCodes.put(token,canonical);if(converted.containsKey(canonical))continue;
                String source=values.get(canonical);var rule=fields.rule(canonical);String type=rule.get("conversion_type").toString();
                if(fixedValues!=null) {
                    // Contract number/date are SQL-formatted metadata; never send them to OpenAI.
                    boolean text=Set.of("{{BORROWER_NAME}}","{{BORROWER_SWD}}","{{VEHICLE}}","{{GUARANTOR_1_NAME}}","{{GUARANTOR_2_NAME}}","{{BORROWER_ADDRESS}}").contains(canonical);
                    type=text?"TRANSLITERATE":canonical.equals("{{TOTAL_OS_WORDS}}")?"FORMAT":"PRESERVE";
                    rule=Map.of("conversion_type",type,"context",canonical.equals("{{BORROWER_ADDRESS}}")?"address":canonical.equals("{{VEHICLE}}")?"vehicle":"personal_name","format_kind","AMOUNT_WORDS");
                }
                Map<String,Object> row=new LinkedHashMap<>();row.put("code",canonical);row.put("source",source);row.put("value",source);row.put("conversion",type);converted.put(canonical,row);
                if(!"hi".equals(language)||source.isBlank()||type.equals("PRESERVE"))continue;
                if(source.matches("(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9-]+")) {row.put("conversion","PRESERVE");continue;}
                if(type.equals("FORMAT")) {
                    String input=source;
                    if(fixedValues!=null&&canonical.equals("{{TOTAL_OS_WORDS}}"))input=fixedValues.get("{{TOTAL_OS}}");
                    if(canonical.equals("{{LOAN_AMOUNT_IN_WORDS}}"))input=String.valueOf(contract.getOrDefault("loan_amount",""));
                    if(canonical.equals("{{CONTRACT_CONTRACT_DATE}}"))input=String.valueOf(contract.getOrDefault("contract_date",""));
                    row.put("value",HindiFormats.format((String)rule.get("format_kind"),input));
                } else if(source.codePoints().anyMatch(c -> c>='A'&&c<='Z'||c>='a'&&c<='z'))
                    toConvert.add(new TranslationCacheService.Field(canonical,source,type,rule.get("context").toString()));
            }
            for(var row:cache.convert(id,toConvert))converted.put(row.get("code").toString(),row);
            long hits=converted.values().stream().filter(r -> Boolean.TRUE.equals(r.get("cacheHit"))).count();
            long misses=converted.values().stream().filter(r -> Boolean.FALSE.equals(r.get("cacheHit"))).count();
            Map<String,Object> preview=new LinkedHashMap<>();preview.put("generationId",id.toString());preview.put("language",language);
            preview.put("fields",new ArrayList<>(converted.values()));preview.put("tokens",tokenCodes);preview.put("cacheHits",hits);preview.put("cacheMisses",misses);
            if(fixedValues!=null)preview.put("fixedReport","REMINDER_LETTER");
            jdbc.update("UPDATE document_generation_audit SET status='PREVIEW',preview=?::jsonb,template_hash=?,cache_hits=?,cache_misses=? WHERE id=?",
                DocumentJson.write(preview),fingerprint,hits,misses,id);
            return preview;
        } catch(Exception e) {
            jdbc.update("UPDATE document_generation_audit SET status='FAILED',failure_reason=?,completed_at=CURRENT_TIMESTAMP WHERE id=?",safeFailure(e),id);
            if(e instanceof RuntimeException runtime)throw runtime;throw new IllegalArgumentException("Document preview failed",e);
        }
    }
    private String safeFailure(Exception e) {
        // Never copy SQL, request payloads, access tokens or provider bodies into audit logs.
        return e instanceof IllegalArgumentException?String.valueOf(e.getMessage()).substring(0,Math.min(450,String.valueOf(e.getMessage()).length())):e.getClass().getSimpleName();
    }
    public Map<String,Object> owned(UUID id,Authentication auth) {
        var rows=jdbc.queryForList("SELECT * FROM document_generation_audit WHERE id=? AND username=? AND user_id IS NOT DISTINCT FROM ? FOR UPDATE",id,user(auth),userId(auth));
        if(rows.isEmpty())throw new AccessDeniedException("Generation not accessible");var row=rows.getFirst();
        accessibleContract(((Number)row.get("contract_id")).longValue(),auth);
        if(!Set.of("PREVIEW","APPROVED").contains(row.get("status")))throw new IllegalArgumentException("Generate a new preview for this attempt");
        if(((java.sql.Timestamp)row.get("expires_at")).toInstant().isBefore(Instant.now()))throw new IllegalArgumentException("Preview expired; generate a new preview");
        return row;
    }
    public void validateSelection(UUID id,GenerateWordDocumentRequest request,Authentication auth) {
        var row=owned(id,auth);
        if(((Number)row.get("contract_id")).longValue()!=request.contractId()
            || ((Number)row.get("template_id")).longValue()!=request.templateId()
            || !row.get("language_code").equals(request.languageCode()))throw new IllegalArgumentException("Preview belongs to a different document selection");
    }
    @SuppressWarnings("unchecked")
    @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> approve(UUID id,Map<String,String> corrections,boolean canCorrect,Authentication auth) {
        var audit=owned(id,auth);var preview=DocumentJson.map(audit.get("preview").toString());
        if(!"PREVIEW".equals(audit.get("status")))throw new IllegalArgumentException("Preview is already approved; create a new preview to change it");
        List<Map<String,Object>> rows=(List<Map<String,Object>>)preview.get("fields");
        if(!canCorrect&&!corrections.isEmpty())throw new AccessDeniedException("Explicit correction permission is required");
        Set<String> editable=new HashSet<>();rows.stream().filter(r -> r.get("cacheId")!=null).forEach(r -> editable.add(r.get("code").toString()));
        if(!editable.containsAll(corrections.keySet()))throw new IllegalArgumentException("Only translated text values may be corrected");
        Map<Long,Map<String,Object>> approved=new HashMap<>();
        for(var row:rows)if(row.get("cacheId")!=null) {
            long cacheId=((Number)row.get("cacheId")).longValue();
            String text=corrections.getOrDefault(row.get("code").toString(),row.get("value").toString());
            var result=approved.get(cacheId);
            if(result==null) {result=cache.approve(cacheId,((Number)row.get("version")).intValue(),text,user(auth),"Document preview approval");approved.put(cacheId,result);}
            else if(!result.get("translated_value").equals(text))throw new IllegalArgumentException("Conflicting corrections for the same cached value");
            row.put("value",result.get("translated_value"));row.put("version",result.get("version"));row.put("verificationStatus","APPROVED");
        }
        jdbc.update("UPDATE document_generation_audit SET preview=?::jsonb,status='APPROVED',approved=TRUE,approved_by=?,approved_at=CURRENT_TIMESTAMP WHERE id=?",DocumentJson.write(preview),user(auth),id);
        return preview;
    }
    @SuppressWarnings("unchecked")
    public WordDocumentGenerationService.GeneratedWordDocument finalizeDocument(UUID id,Authentication auth) {
        var audit=owned(id,auth);
        if("hi".equals(audit.get("language_code"))&&!Boolean.TRUE.equals(audit.get("approved")))throw new IllegalArgumentException("Review and approve Hindi values first");
        // Atomically consume the preview; one attempt cannot finalize twice.
        if(jdbc.update("UPDATE document_generation_audit SET status='FINALIZING' WHERE id=? AND status IN ('PREVIEW','APPROVED')",id)!=1)throw new IllegalArgumentException("Generation already in progress");
        WordDocumentGenerationService.GeneratedWordDocument rendered=null;
        try {
            var preview=DocumentJson.map(audit.get("preview").toString());List<Map<String,Object>> rows=(List<Map<String,Object>>)preview.get("fields");
            var current="REMINDER_LETTER".equals(preview.get("fixedReport"))
                ?ReminderLetterService.values(jdbc.queryForObject("SELECT document_values FROM document_export_items WHERE job_id=? AND contract_id=?",String.class,audit.get("batch_id"),audit.get("contract_id")))
                :generation.resolveContractValues(accessibleContract(((Number)audit.get("contract_id")).longValue(),auth));
            if(rows.stream().anyMatch(row -> !Objects.equals(current.get(row.get("code").toString()),row.get("source"))))throw new IllegalArgumentException("Source values changed; generate a new preview");
            rendered=render(audit);
            for(var row:rows)if(row.get("cacheId")!=null)jdbc.update("INSERT INTO document_generation_translation_versions(generation_id,canonical_code,cache_id,cache_version) VALUES(?,?,?,?)",id,row.get("code"),row.get("cacheId"),row.get("version"));
            jdbc.update("UPDATE document_generation_audit SET status='SUCCESS',filename=?,preview=NULL,completed_at=CURRENT_TIMESTAMP WHERE id=?",rendered.filename(),id);
            return rendered;
        }catch(Exception e){
            if(rendered!=null)try{Files.deleteIfExists(rendered.resource().getFile().toPath());}catch(java.io.IOException ignored){}
            jdbc.update("UPDATE document_generation_audit SET status='FAILED',preview=NULL,failure_reason=?,completed_at=CURRENT_TIMESTAMP WHERE id=?",safeFailure(e),id);if(e instanceof RuntimeException runtime)throw runtime;throw new IllegalArgumentException("Document generation failed",e);
        }
    }
    public WordDocumentGenerationService.GeneratedWordDocument previewDocument(UUID id,Authentication auth) {
        var audit=owned(id,auth);
        try {
            var rendered=render(audit);
            return new WordDocumentGenerationService.GeneratedWordDocument("PREVIEW-"+rendered.filename(),rendered.resource());
        } catch(java.io.IOException e) { throw new IllegalArgumentException("Unable to render document preview",e); }
    }
    @SuppressWarnings("unchecked")
    private WordDocumentGenerationService.GeneratedWordDocument render(Map<String,Object> audit) throws java.io.IOException {
            var template=templates.findTemplate(((Number)audit.get("template_id")).longValue()).orElseThrow();
            if(!Boolean.TRUE.equals(template.isActive())||!template.languageCode().equals(audit.get("language_code")))throw new IllegalArgumentException("Template changed; generate a new preview");
            var file=templates.findTemplateFile(template.id()).orElseThrow();var path=storage.path(file.storagePath());
            if(!TranslationCacheService.hash(HexFormat.of().formatHex(Files.readAllBytes(path))).equals(audit.get("template_hash")))throw new IllegalArgumentException("Template file changed; generate a new preview");
            var preview=DocumentJson.map(audit.get("preview").toString());List<Map<String,Object>> rows=(List<Map<String,Object>>)preview.get("fields");
            Map<String,String> canonical=new HashMap<>();rows.forEach(r -> canonical.put(r.get("code").toString(),r.get("value").toString()));
            Map<String,String> values=new HashMap<>();((Map<String,String>)preview.get("tokens")).forEach((token,code)->values.put(token,canonical.get(code)));
            Path output=Files.createTempFile("dugar-document-",".docx");
            try {engine.merge(path,output,values);}catch(RuntimeException error){Files.deleteIfExists(output);throw error;}
            String filename=String.valueOf(audit.get("contract_number")).replaceAll("[^A-Za-z0-9._-]","_")+"-"+file.originalFilename();
            return new WordDocumentGenerationService.GeneratedWordDocument(filename,new TemporaryDocumentResource(output));
    }
}
