package dugar_lms_api.modules.wordtemplates.automation;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.wordtemplates.dto.GenerateWordDocumentRequest;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/document-automation")
public class DocumentAutomationController {
    public static final String MASTER="ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER",GENERATE="OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT",
        REPORT="ADMINISTRATION_REPORTS_AI_USAGE_BILLING",SETTINGS="ADMINISTRATION_MASTERS_DOCUMENT_AI_SETTINGS";
    @org.springframework.beans.factory.annotation.Autowired private DocumentBatchService batches;
    private final MenuAccessService access;private final JdbcTemplate jdbc;private final DocumentFieldService fields;
    private final DocumentAutomationService automation;private final TranslationCacheService cache;private final DocumentUsageReportService reports;
    public DocumentAutomationController(MenuAccessService access,JdbcTemplate jdbc,DocumentFieldService fields,DocumentAutomationService automation,TranslationCacheService cache,DocumentUsageReportService reports) {
        this.access=access;this.jdbc=jdbc;this.fields=fields;this.automation=automation;this.cache=cache;this.reports=reports;
    }
    private void require(String menu,Authentication auth){if(auth==null||!access.canView(menu,auth))throw new AccessDeniedException("Document permission required");}
    @GetMapping("/languages") public Object languages(Authentication auth){if(!access.canView(MASTER,auth))require(GENERATE,auth);return jdbc.queryForList("SELECT code,language_name FROM document_languages WHERE is_active ORDER BY code");}
    @GetMapping("/contract-fields")
    public Object contractFields(@RequestParam String contractNumber, Authentication auth) {
        require(MASTER, auth);
        return automation.contractFieldValues(contractNumber, auth);
    }
    @GetMapping("/fields") public Object fields(Authentication auth){require(MASTER,auth);return fields.configuration();}
    public record AliasRequest(Long id,String alias,String canonicalCode) {}
    @PostMapping("/aliases") public void alias(@RequestBody AliasRequest r,Authentication auth){require(MASTER,auth);fields.saveAlias(r.id(),r.alias(),r.canonicalCode(),DocumentAutomationService.user(auth));}
    @DeleteMapping("/aliases/{id}") public void deleteAlias(@PathVariable long id,Authentication auth){require(MASTER,auth);fields.deleteAlias(id);}
    public record RuleRequest(String canonicalCode,String conversionType,String context,String formatKind) {}
    @PutMapping("/fields") public void rule(@RequestBody RuleRequest r,Authentication auth){require(MASTER,auth);fields.saveRule(r.canonicalCode(),r.conversionType(),r.context(),r.formatKind(),DocumentAutomationService.user(auth));}
    @PostMapping("/preview") public Object preview(@Valid @RequestBody GenerateWordDocumentRequest r,Authentication auth){require(GENERATE,auth);return automation.preview(r,r.languageCode(),auth);}
    @PostMapping("/bulk-preview") public Object bulk(@RequestBody List<@Valid GenerateWordDocumentRequest> requests,Authentication auth){require(GENERATE,auth);return batches.preview(requests,auth);}
    @GetMapping("/capabilities") public Object capabilities(Authentication auth){require(GENERATE,auth);return Map.of("canCorrect",canCorrect(auth),"formats",List.of("DOCX"));}
    private boolean canCorrect(Authentication auth) {
        if(access.canView(MASTER,auth))return true;
        Boolean allowed=jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM role_permissions p JOIN roles r USING(role_id) JOIN menus m USING(menu_id)
                WHERE m.menu_code=? AND r.is_active AND p.can_view AND p.can_edit AND
                (EXISTS(SELECT 1 FROM user_roles ur WHERE ur.role_id=r.role_id AND ur.user_id=?)
                 OR EXISTS(SELECT 1 FROM users u WHERE u.role_id=r.role_id AND u.user_id=?)))
            """,Boolean.class,GENERATE,DocumentAutomationService.userId(auth),DocumentAutomationService.userId(auth));return Boolean.TRUE.equals(allowed);
    }
    public record Approval(Map<String,String> corrections) {}
    @PostMapping("/preview/{id}/approve") public Object approve(@PathVariable UUID id,@RequestBody Approval r,Authentication auth){require(GENERATE,auth);return automation.approve(id,r.corrections()==null?Map.of():r.corrections(),canCorrect(auth),auth);}
    @GetMapping("/preview/{id}/download") public ResponseEntity<?> previewDownload(@PathVariable UUID id,Authentication auth) {
        require(GENERATE,auth);var document=automation.previewDocument(id,auth);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(document.filename(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(document.resource());
    }
    @GetMapping("/cache") public Object cache(@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="0") int page,Authentication auth) {
        require(SETTINGS,auth);String like="%"+keyword+"%";
        return Map.of("rows",jdbc.queryForList("SELECT * FROM document_translation_cache WHERE source_value ILIKE ? OR translated_value ILIKE ? ORDER BY updated_at DESC LIMIT 50 OFFSET ?",like,like,Math.max(0,page)*50),"total",jdbc.queryForObject("SELECT COUNT(*) FROM document_translation_cache WHERE source_value ILIKE ? OR translated_value ILIKE ?",Long.class,like,like));
    }
    public record Correction(int version,String text,String reason) {}
    @PostMapping("/cache/{id}/approve") public Object correct(@PathVariable long id,@RequestBody Correction r,Authentication auth){require(SETTINGS,auth);if(r.reason()==null||r.reason().isBlank()||r.reason().length()>500)throw new IllegalArgumentException("Correction reason is required");return cache.approve(id,r.version(),r.text(),DocumentAutomationService.user(auth),r.reason());}
    @GetMapping("/cache/{id}/history") public Object history(@PathVariable long id,Authentication auth){require(SETTINGS,auth);return jdbc.queryForList("SELECT * FROM document_translation_history WHERE cache_id=? ORDER BY version",id);}
    @GetMapping("/report") public Object report(@RequestParam Map<String,String> filters,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,Authentication auth){require(REPORT,auth);return reports.report(filters,auth,page,size);}
    @GetMapping("/report/{id}") public Object detail(@PathVariable UUID id,Authentication auth){require(REPORT,auth);return reports.detail(id,auth);}
    @GetMapping("/report-export") public ResponseEntity<byte[]> export(@RequestParam Map<String,String> filters,Authentication auth){require(REPORT,auth);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=ai-usage.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(reports.excel(filters,auth));}
    @GetMapping(value="/report-print",produces="text/html;charset=UTF-8") public String print(@RequestParam Map<String,String> filters,Authentication auth){require(REPORT,auth);return reports.printHtml(filters,auth);}
    @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<?> denied(AccessDeniedException e){return ResponseEntity.status(403).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.dao.DataIntegrityViolationException.class}) public ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("message",e instanceof IllegalArgumentException?e.getMessage():"Duplicate alias, template language or invalid document configuration"));}
}
