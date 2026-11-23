package dugar_lms_api.modules.wordtemplates.service;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateDto;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateMetadataRequest;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateValidationDto;
import dugar_lms_api.modules.wordtemplates.model.WordTemplateFile;
import dugar_lms_api.modules.wordtemplates.repository.WordTemplateRepository;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WordTemplateService {
    @org.springframework.beans.factory.annotation.Autowired
    private dugar_lms_api.modules.wordtemplates.automation.DocumentFieldService documentFields;


    private static final String WORD_TEMPLATE_MENU_CODE = "ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER";
    private static final String GENERATE_MENU_CODE = "OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT";

    private final WordTemplateRepository repository;
    private final WordTemplateStorageService storageService;
    private final MenuAccessService menuAccessService;
    private final WordPlaceholderRegistry placeholderRegistry;
    private final WordDocxTemplateEngine docxTemplateEngine;

    public WordTemplateService(
        WordTemplateRepository repository,
        WordTemplateStorageService storageService,
        MenuAccessService menuAccessService,
        WordPlaceholderRegistry placeholderRegistry,
        WordDocxTemplateEngine docxTemplateEngine
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.menuAccessService = menuAccessService;
        this.placeholderRegistry = placeholderRegistry;
        this.docxTemplateEngine = docxTemplateEngine;
    }

    public List<WordTemplateDto> templates(String keyword,Boolean active,Authentication auth) {
        boolean management=requireCatalogAccess(auth);
        return repository.findTemplates(keyword,management?active:Boolean.TRUE).stream()
            .map(t -> availability(t,validFile(t))).filter(t -> management||Boolean.TRUE.equals(t.hasFile())).toList();
    }
    private AccessDeniedException definitions(){return new AccessDeniedException("Template definitions are registered by developers; only DOCX upload and activation are allowed.");}
    private boolean requireCatalogAccess(Authentication authentication) {
        if (menuAccessService.canView(WORD_TEMPLATE_MENU_CODE, authentication)) return true;
        if (menuAccessService.canView(GENERATE_MENU_CODE, authentication)) return false;
        throw new AccessDeniedException("Document template catalog access denied.");
    }

    public WordTemplateDto createTemplate(WordTemplateMetadataRequest r,MultipartFile file,Authentication auth){requireAccess(auth);throw definitions();}
    public WordTemplateDto updateTemplate(Long id,WordTemplateMetadataRequest r,Authentication auth){requireAccess(auth);throw definitions();}
    public WordTemplateDto updateTemplateWithFile(Long id,WordTemplateMetadataRequest r,MultipartFile file,Authentication auth){requireAccess(auth);throw definitions();}
    @Transactional
    public WordTemplateDto setActive(Long id,boolean active,Authentication auth) {
        requireAccess(auth);repository.lock(id);var template=requireTemplate(id);
        if(active&&!validFile(template))throw new IllegalArgumentException("Upload a valid DOCX with the required placeholders before activation.");
        var result=repository.setActive(id,active,auditUser(auth));var file=requireTemplateFile(id);
        repository.audit(id,active?"ACTIVATE":"DEACTIVATE",file.originalFilename(),file.storagePath(),null,auditUser(auth));
        return availability(result,validFile(result));
    }
    @Transactional
    public WordTemplateDto replaceTemplateFile(Long id, MultipartFile file, Authentication authentication) {
        requireAccess(authentication);
        repository.lock(id);
        var template = requireTemplate(id);
        WordTemplateFile existing = requireTemplateFile(id);
        WordTemplateStorageService.StoredTemplateFile stored = storageService.store(file);
        try {
            validateFile(template,stored.storagePath());
            WordTemplateDto updated = repository.replaceTemplateFile(id, stored.originalFilename(), stored.storagePath(), auditUser(authentication));
            WordTemplateDto validated = withValidation(updated, stored.storagePath());
            repository.audit(id,existing.storagePath()==null||existing.storagePath().isBlank()?"UPLOAD":"REPLACE",stored.originalFilename(),stored.storagePath(),dugar_lms_api.modules.wordtemplates.automation.DocumentExportService.fingerprint(storageService.path(stored.storagePath())),auditUser(authentication));
            cleanupFileAfterTransaction(existing.storagePath(), stored.storagePath());
            return validated;
        } catch (RuntimeException exception) {
            storageService.deleteQuietly(stored.storagePath());
            throw exception;
        }
    }

    public void deleteTemplate(Long id,Authentication auth){requireAccess(auth);throw definitions();}

    private void validateFile(WordTemplateDto template,String path) {
        storageService.validateStored(path);
        var validation=withValidation(template,path).validation();
        if(!validation.unknown().isEmpty())throw new IllegalArgumentException("Unknown placeholders: "+String.join(", ",validation.unknown()));
        if(dugar_lms_api.modules.wordtemplates.automation.ReminderLetterService.isReminder(template)) {
            var required=dugar_lms_api.modules.wordtemplates.automation.ReminderLetterService.CODES.stream().map(c -> "{{"+c+"}}").collect(java.util.stream.Collectors.toSet());
            required.removeAll(Set.of("{{CONTRACT_NUMBER}}", "{{CONTRACT_DATE}}"));
            required.removeAll(validation.placeholders());
            if(!required.isEmpty())throw new IllegalArgumentException("Missing required placeholders: "+String.join(", ",required));
        }
    }
    private boolean validFile(WordTemplateDto template) {
        try {var file=requireTemplateFile(template.id());validateFile(template,file.storagePath());return true;}
        catch(RuntimeException error){return false;}
    }
    private WordTemplateDto availability(WordTemplateDto t,boolean available){return new WordTemplateDto(t.id(),t.templateName(),t.originalFilename(),t.displayOrder(),t.isActive(),t.createdAt(),t.createdBy(),t.updatedAt(),t.updatedBy(),t.validation(),t.languageCode(),t.documentKey(),available);}
    private void cleanupFileAfterTransaction(String previous, String stored) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        String path = status == STATUS_COMMITTED ? previous : stored;
                        if (path != null) storageService.deleteQuietly(path);
                    }
                });
        } else if (previous != null) storageService.deleteQuietly(previous);
    }

    public DownloadedTemplate download(Long id, Authentication authentication) {
        boolean management = requireCatalogAccess(authentication);
        if (!management && (!Boolean.TRUE.equals(requireTemplate(id).isActive()) || !validFile(requireTemplate(id)))) throw new AccessDeniedException("Template is inactive.");
        WordTemplateFile file = requireTemplateFile(id);
        return new DownloadedTemplate(file.originalFilename(), storageService.resource(file.storagePath()));
    }

    private void requireAccess(Authentication authentication) {
        if (!menuAccessService.canView(WORD_TEMPLATE_MENU_CODE, authentication)) {
            throw new AccessDeniedException("Document Template Master access denied.");
        }
    }

    private WordTemplateDto requireTemplate(Long id) {
        return repository.findTemplate(id)
            .orElseThrow(() -> new IllegalArgumentException("Document Template not found."));
    }

    private WordTemplateFile requireTemplateFile(Long id) {
        return repository.findTemplateFile(id)
            .orElseThrow(() -> new IllegalArgumentException("Document Template not found."));
    }

    private String auditUser(Authentication authentication) {
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) {
                return String.valueOf(userId);
            }
        }
        return "system";
    }

    private WordTemplateDto withValidation(WordTemplateDto template, String storagePath) {
        String dataSource = WordPlaceholderRegistry.CONTRACT;
        Set<String> known = dugar_lms_api.modules.wordtemplates.automation.ReminderLetterService.isReminder(template)
            ?dugar_lms_api.modules.wordtemplates.automation.ReminderLetterService.CODES.stream().map(c -> "{{"+c+"}}").collect(java.util.stream.Collectors.toSet())
            :placeholderRegistry.knownCodes(dataSource);
        List<String> placeholders = docxTemplateEngine.extractPlaceholders(storageService.path(storagePath)).stream().toList();
        WordTemplateValidationDto validation = new WordTemplateValidationDto(
            placeholders,
            placeholders.stream().filter(code -> known.contains(code) || (documentFields != null && known.contains(documentFields.canonical(code, documentFields.aliases())))).toList(),
            placeholders.stream().filter(code -> !known.contains(code) && !(documentFields != null && known.contains(documentFields.canonical(code, documentFields.aliases())))).toList()
        );
        return new WordTemplateDto(
            template.id(),
            template.templateName(),
            template.originalFilename(),
            template.displayOrder(),
            template.isActive(),
            template.createdAt(),
            template.createdBy(),
            template.updatedAt(),
            template.updatedBy(),
            validation, template.languageCode(), template.documentKey(), true
        );
    }

    public record DownloadedTemplate(String filename, Resource resource) {
    }
}
