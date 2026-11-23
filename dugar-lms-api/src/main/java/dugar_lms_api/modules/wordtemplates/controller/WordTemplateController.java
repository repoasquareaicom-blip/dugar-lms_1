package dugar_lms_api.modules.wordtemplates.controller;

import dugar_lms_api.modules.wordtemplates.dto.WordTemplateDto;
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
    public record ActivationRequest(@jakarta.validation.constraints.NotNull Boolean isActive) {}
    @PutMapping("/{id}/active")
    public WordTemplateDto setActive(@PathVariable Long id,@Valid @RequestBody ActivationRequest request,Authentication auth) {
        return service.setActive(id,request.isActive(),auth);
    }

    @GetMapping
    public ResponseEntity<List<WordTemplateDto>> templates(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean isActive,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.templates(keyword, isActive, authentication));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WordTemplateDto> createTemplate(
        @Valid @ModelAttribute WordTemplateMetadataRequest request,
        @RequestPart("file") MultipartFile file,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTemplate(request, file, authentication));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WordTemplateDto> updateTemplate(
        @PathVariable Long id,
        @Valid @RequestBody WordTemplateMetadataRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.updateTemplate(id, request, authentication));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WordTemplateDto> updateTemplateWithFile(
        @PathVariable Long id,
        @Valid @ModelAttribute WordTemplateMetadataRequest request,
        @RequestPart(value = "file", required = false) MultipartFile file,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.updateTemplateWithFile(id, request, file, authentication));
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
                .filename(download.filename(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(download.resource());
    }

    @org.springframework.beans.factory.annotation.Autowired
    private dugar_lms_api.modules.wordtemplates.automation.DocumentPdfConverter pdf;
    @GetMapping("/{id}/preview")
    public ResponseEntity<?> previewOriginal(@PathVariable Long id,Authentication auth) throws java.io.IOException {
        var original=service.download(id,auth); // Same authorization; no contract resolution, merge or AI.
        java.nio.file.Path input=java.nio.file.Files.createTempFile("dugar-original-",".docx");
        try {
            try(var stream=original.resource().getInputStream()){java.nio.file.Files.copy(stream,input,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            var converted=pdf.convert(input);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=original-template.pdf")
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(new dugar_lms_api.modules.wordtemplates.automation.TemporaryDocumentResource(converted));
        } finally {java.nio.file.Files.deleteIfExists(input);}
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
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Duplicate or invalid Document Template record."));
    }
}
