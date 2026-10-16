package dugar_lms_api.modules.wordtemplates.controller;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.wordtemplates.dto.GenerateWordDocumentRequest;
import dugar_lms_api.modules.wordtemplates.dto.WordContractOptionDto;
import dugar_lms_api.modules.wordtemplates.dto.WordPlaceholderFieldDto;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateValidationDto;
import dugar_lms_api.modules.wordtemplates.service.WordDocumentGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/word-documents")
public class WordDocumentGenerationController {

    private static final String WORD_TEMPLATE_MENU_CODE = "ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER";
    private static final String GENERATE_MENU_CODE = "OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT";
    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final WordDocumentGenerationService service;
    private final MenuAccessService menuAccessService;

    public WordDocumentGenerationController(WordDocumentGenerationService service, MenuAccessService menuAccessService) {
        this.service = service;
        this.menuAccessService = menuAccessService;
    }

    @GetMapping("/placeholders")
    public ResponseEntity<List<WordPlaceholderFieldDto>> placeholders(
        @RequestParam(defaultValue = "CONTRACT") String dataSource,
        Authentication authentication
    ) {
        requireMenu(WORD_TEMPLATE_MENU_CODE, authentication);
        return ResponseEntity.ok(service.placeholders(dataSource));
    }

    @GetMapping("/templates/{id}/validation")
    public ResponseEntity<WordTemplateValidationDto> validate(
        @PathVariable Long id,
        @RequestParam(defaultValue = "CONTRACT") String dataSource,
        Authentication authentication
    ) {
        requireMenu(WORD_TEMPLATE_MENU_CODE, authentication);
        return ResponseEntity.ok(service.validate(id, dataSource));
    }

    @GetMapping("/contracts")
    public ResponseEntity<List<WordContractOptionDto>> contracts(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "20") int limit,
        Authentication authentication
    ) {
        requireMenu(GENERATE_MENU_CODE, authentication);
        return ResponseEntity.ok(service.searchContracts(keyword, limit, authentication));
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(
        @Valid @RequestBody GenerateWordDocumentRequest request,
        Authentication authentication
    ) {
        requireMenu(GENERATE_MENU_CODE, authentication);
        WordDocumentGenerationService.GeneratedWordDocument generated = service.generate(request, authentication);
        return ResponseEntity.ok()
            .contentType(DOCX_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(generated.filename())
                .build()
                .toString())
            .body(generated.resource());
    }

    private void requireMenu(String menuCode, Authentication authentication) {
        if (!menuAccessService.canView(menuCode, authentication)) {
            throw new AccessDeniedException("Word document access denied.");
        }
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage() == null ? "Invalid request." : error.getDefaultMessage())
            .orElse("Invalid request.");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
