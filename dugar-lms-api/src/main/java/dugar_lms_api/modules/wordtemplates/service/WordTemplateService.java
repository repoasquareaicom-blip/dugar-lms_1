package dugar_lms_api.modules.wordtemplates.service;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateDto;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateGroupDto;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateGroupRequest;
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

    private static final String WORD_TEMPLATE_MENU_CODE = "ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER";

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

    public List<WordTemplateGroupDto> groups(String keyword, Boolean isActive, Authentication authentication) {
        requireAccess(authentication);
        return repository.findGroups(keyword, isActive);
    }

    @Transactional
    public WordTemplateGroupDto createGroup(WordTemplateGroupRequest request, Authentication authentication) {
        requireAccess(authentication);
        requireUniqueGroupName(request.groupName(), null);
        return repository.createGroup(request, auditUser(authentication));
    }

    @Transactional
    public WordTemplateGroupDto updateGroup(Long id, WordTemplateGroupRequest request, Authentication authentication) {
        requireAccess(authentication);
        requireGroup(id);
        requireUniqueGroupName(request.groupName(), id);
        return repository.updateGroup(id, request, auditUser(authentication));
    }

    @Transactional
    public void deleteGroup(Long id, Authentication authentication) {
        requireAccess(authentication);
        requireGroup(id);
        if (repository.countTemplates(id) > 0) {
            throw new IllegalArgumentException("Cannot delete a group that contains templates.");
        }
        repository.deleteGroup(id);
    }

    public List<WordTemplateDto> templates(Long groupId, String keyword, Boolean isActive, Authentication authentication) {
        requireAccess(authentication);
        return repository.findTemplates(groupId, keyword, isActive);
    }

    @Transactional
    public WordTemplateDto createTemplate(
        WordTemplateMetadataRequest request,
        MultipartFile file,
        Authentication authentication
    ) {
        requireAccess(authentication);
        requireGroup(request.groupId());
        WordTemplateStorageService.StoredTemplateFile stored = storageService.store(file);
        try {
            WordTemplateDto created = repository.createTemplate(request, stored.originalFilename(), stored.storagePath(), auditUser(authentication));
            return withValidation(created, stored.storagePath(), request.groupId());
        } catch (RuntimeException exception) {
            storageService.deleteQuietly(stored.storagePath());
            throw exception;
        }
    }

    @Transactional
    public WordTemplateDto updateTemplate(Long id, WordTemplateMetadataRequest request, Authentication authentication) {
        requireAccess(authentication);
        requireTemplate(id);
        requireGroup(request.groupId());
        return repository.updateTemplate(id, request, auditUser(authentication));
    }

    @Transactional
    public WordTemplateDto replaceTemplateFile(Long id, MultipartFile file, Authentication authentication) {
        requireAccess(authentication);
        WordTemplateFile existing = requireTemplateFile(id);
        WordTemplateStorageService.StoredTemplateFile stored = storageService.store(file);
        try {
            WordTemplateDto updated = repository.replaceTemplateFile(id, stored.originalFilename(), stored.storagePath(), auditUser(authentication));
            storageService.deleteQuietly(existing.storagePath());
            return withValidation(updated, stored.storagePath(), updated.groupId());
        } catch (RuntimeException exception) {
            storageService.deleteQuietly(stored.storagePath());
            throw exception;
        }
    }

    @Transactional
    public void deleteTemplate(Long id, Authentication authentication) {
        requireAccess(authentication);
        WordTemplateFile existing = requireTemplateFile(id);
        repository.deleteTemplate(id);
        storageService.deleteQuietly(existing.storagePath());
    }

    public DownloadedTemplate download(Long id, Authentication authentication) {
        requireAccess(authentication);
        WordTemplateFile file = requireTemplateFile(id);
        return new DownloadedTemplate(file.originalFilename(), storageService.resource(file.storagePath()));
    }

    private void requireAccess(Authentication authentication) {
        if (!menuAccessService.canView(WORD_TEMPLATE_MENU_CODE, authentication)) {
            throw new AccessDeniedException("Word Template Master access denied.");
        }
    }

    private WordTemplateGroupDto requireGroup(Long id) {
        return repository.findGroup(id)
            .orElseThrow(() -> new IllegalArgumentException("Template Group not found."));
    }

    private void requireUniqueGroupName(String groupName, Long excludeId) {
        if (repository.groupNameExists(groupName, excludeId)) {
            throw new IllegalArgumentException("Template Group name already exists.");
        }
    }

    private WordTemplateDto requireTemplate(Long id) {
        return repository.findTemplate(id)
            .orElseThrow(() -> new IllegalArgumentException("Word Template not found."));
    }

    private WordTemplateFile requireTemplateFile(Long id) {
        return repository.findTemplateFile(id)
            .orElseThrow(() -> new IllegalArgumentException("Word Template not found."));
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

    private WordTemplateDto withValidation(WordTemplateDto template, String storagePath, Long groupId) {
        String dataSource = repository.findGroup(groupId).map(WordTemplateGroupDto::dataSource).orElse(WordPlaceholderRegistry.CONTRACT);
        Set<String> known = placeholderRegistry.knownCodes(dataSource);
        List<String> placeholders = docxTemplateEngine.extractPlaceholders(storageService.path(storagePath)).stream().toList();
        WordTemplateValidationDto validation = new WordTemplateValidationDto(
            placeholders,
            placeholders.stream().filter(known::contains).toList(),
            placeholders.stream().filter(code -> !known.contains(code)).toList()
        );
        return new WordTemplateDto(
            template.id(),
            template.groupId(),
            template.groupName(),
            template.templateName(),
            template.originalFilename(),
            template.displayOrder(),
            template.isActive(),
            template.createdAt(),
            template.createdBy(),
            template.updatedAt(),
            template.updatedBy(),
            validation
        );
    }

    public record DownloadedTemplate(String filename, Resource resource) {
    }
}
