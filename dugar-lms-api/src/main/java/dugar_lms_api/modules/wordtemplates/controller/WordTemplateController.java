package dugar_lms_api.modules.wordtemplates.controller;

import dugar_lms_api.modules.wordtemplates.dto.WordTemplateDto;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateGroupDto;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateGroupRequest;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateMetadataRequest;
import dugar_lms_api.modules.wordtemplates.service.WordTemplateService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/word-templates")
public class WordTemplateController {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final WordTemplateService service;

    public WordTemplateController(WordTemplateService service) {
        this.service = service;
    }

    @GetMapping("/groups")
    public ResponseEntity<List<WordTemplateGroupDto>> groups(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean isActive,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.groups(keyword, isActive, authentication));
    }

    @PostMapping("/groups")
    public ResponseEntity<WordTemplateGroupDto> createGroup(
        @Valid @RequestBody WordTemplateGroupRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGroup(request, authentication));
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<WordTemplateGroupDto> updateGroup(
        @PathVariable Long id,
        @Valid @RequestBody WordTemplateGroupRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.updateGroup(id, request, authentication));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id, Authentication authentication) {
        service.deleteGroup(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<WordTemplateDto>> templates(
        @RequestParam(required = false) Long groupId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean isActive,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.templates(groupId, keyword, isActive, authentication));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WordTemplateDto> createTemplate(
        @Valid @ModelAttribute WordTemplateMetadataRequest request,
        @RequestPart("file") MultipartFile file,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTemplate(request, file, authentication));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WordTemplateDto> updateTemplate(
        @PathVariable Long id,
        @Valid @RequestBody WordTemplateMetadataRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.updateTemplate(id, request, authentication));
    }

    @PostMapping(path = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WordTemplateDto> replaceTemplateFile(
        @PathVariable Long id,
        @RequestPart("file") MultipartFile file,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.replaceTemplateFile(id, file, authentication));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id, Authentication authentication) {
        WordTemplateService.DownloadedTemplate download = service.download(id, authentication);
        return ResponseEntity.ok()
            .contentType(DOCX_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(download.filename())
                .build()
                .toString())
            .body(download.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id, Authentication authentication) {
        service.deleteTemplate(id, authentication);
        return ResponseEntity.noContent().build();
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Duplicate or invalid Word Template record."));
    }
}
