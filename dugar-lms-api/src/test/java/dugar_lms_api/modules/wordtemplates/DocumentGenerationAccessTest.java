package dugar_lms_api.modules.wordtemplates;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.wordtemplates.controller.WordDocumentGenerationController;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateDto;
import dugar_lms_api.modules.wordtemplates.repository.WordTemplateRepository;
import dugar_lms_api.modules.wordtemplates.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentGenerationAccessTest {
    private final String master = "ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER";
    private final String generate = "OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT";
    private final MenuAccessService access = mock(MenuAccessService.class);
    private final WordTemplateRepository repository = mock(WordTemplateRepository.class);
    private final WordDocumentGenerationService generation = mock(WordDocumentGenerationService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final WordTemplateService templates = new WordTemplateService(repository,
        mock(WordTemplateStorageService.class), access, mock(WordPlaceholderRegistry.class), mock(WordDocxTemplateEngine.class));
    private final WordDocumentGenerationController controller = new WordDocumentGenerationController(generation, access);

    @BeforeEach
    void operationsHasGenerationAccessOnly() {
        when(access.canView(generate, authentication)).thenReturn(true);
    }

    @Test
    void generationRoleCanLoadActiveCatalogEvenWhenRequestingInactiveRecords() {
        when(repository.findTemplates(null, true)).thenReturn(List.of(mock(WordTemplateDto.class)));
        // Unuploaded definitions must never be offered to a generation-only user.
        assertThat(templates.templates(null, false, authentication)).isEmpty();
        verify(repository).findTemplates(null, true);
    }

    @Test
    void generationRoleCanValidateButCannotManageTemplates() {
        controller.validate(10L, "CONTRACT", authentication);
        verify(generation).validate(10L, "CONTRACT");
        assertThatThrownBy(() -> templates.deleteTemplate(10L, authentication)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> templates.createTemplate(null, null, authentication)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.placeholders("CONTRACT", authentication)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void userWithoutEitherMenuCannotReadCatalogOrValidate() {
        when(access.canView(generate, authentication)).thenReturn(false);
        assertThatThrownBy(() -> templates.templates(null, true, authentication)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.validate(10L, "CONTRACT", authentication)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository, generation);
    }

    @Test
    void templateMasterKeepsItsUnfilteredCatalogAccess() {
        when(access.canView(master, authentication)).thenReturn(true);
        templates.templates(null, false, authentication);
        verify(repository).findTemplates(null, false);
        templates.templates(null, null, authentication);
        verify(repository).findTemplates(null, null);
    }
}
