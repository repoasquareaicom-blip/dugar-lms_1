package dugar_lms_api.modules.wordtemplates.service;

import dugar_lms_api.modules.wordtemplates.dto.GenerateWordDocumentRequest;
import dugar_lms_api.modules.wordtemplates.dto.WordPlaceholderFieldDto;
import dugar_lms_api.modules.wordtemplates.model.WordTemplateFile;
import dugar_lms_api.modules.wordtemplates.repository.WordDocumentGenerationRepository;
import dugar_lms_api.modules.wordtemplates.repository.WordTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WordDocumentGenerationServiceTest {

    @Mock
    private WordDocumentGenerationRepository repository;

    @Mock
    private WordTemplateRepository templateRepository;

    @Mock
    private WordTemplateStorageService storageService;

    @Mock
    private WordPlaceholderRegistry placeholderRegistry;

    @Mock
    private WordDocxTemplateEngine docxTemplateEngine;

    @Test
    void resolvesAgreementPageOneDateAndBorrowerState() {
        WordDocumentGenerationService service = new WordDocumentGenerationService(
            repository,
            templateRepository,
            storageService,
            placeholderRegistry,
            docxTemplateEngine
        );
        Path templatePath = Path.of("template.docx");
        when(templateRepository.findTemplateFile(10L)).thenReturn(Optional.of(new WordTemplateFile(10L, "Agreement.docx", "stored.docx")));
        when(storageService.path("stored.docx")).thenReturn(templatePath);
        when(docxTemplateEngine.extractPlaceholders(templatePath)).thenReturn(Set.of("{{CONTRACT_CONTRACT_DATE}}", "{{BORROWER_STATE}}"));
        when(placeholderRegistry.knownCodes(WordPlaceholderRegistry.CONTRACT)).thenReturn(Set.of("{{CONTRACT_CONTRACT_DATE}}", "{{BORROWER_STATE}}"));
        when(placeholderRegistry.fields(WordPlaceholderRegistry.CONTRACT)).thenReturn(List.of(
            new WordPlaceholderFieldDto(WordPlaceholderRegistry.CONTRACT, "Contract", "Contract Date", "{{CONTRACT_CONTRACT_DATE}}", "contracts", "contract_date", false, false),
            new WordPlaceholderFieldDto(WordPlaceholderRegistry.CONTRACT, "Address", "State", "{{BORROWER_STATE}}", "party_masters", "state", false, false)
        ));
        when(repository.findAccessibleContract(eq(1L), any())).thenReturn(Optional.of(Map.of(
            "contract_id", 1L,
            "contract_number", "C-001",
            "contract_date", LocalDate.of(2024, 9, 21),
            "borrower_code", "B001"
        )));
        when(repository.findParty("B001", "BORROWER")).thenReturn(Optional.of(Map.of("state", "S0005")));
        when(repository.findOtherDescription("S0005")).thenReturn(Optional.of("MAHARASHTRA"));

        service.generate(new GenerateWordDocumentRequest(WordPlaceholderRegistry.CONTRACT, 1L, 10L), null);

        ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
        verify(docxTemplateEngine).merge(eq(templatePath), any(Path.class), values.capture());
        assertThat(values.getValue())
            .containsEntry("{{CONTRACT_CONTRACT_DATE}}", "21st Sep 2024")
            .containsEntry("{{BORROWER_STATE}}", "MAHARASHTRA");
    }

    @Test
    void usesThSuffixForTeenContractDates() {
        WordDocumentGenerationService service = new WordDocumentGenerationService(
            repository,
            templateRepository,
            storageService,
            placeholderRegistry,
            docxTemplateEngine
        );
        Path templatePath = Path.of("template.docx");
        when(templateRepository.findTemplateFile(10L)).thenReturn(Optional.of(new WordTemplateFile(10L, "Agreement.docx", "stored.docx")));
        when(storageService.path("stored.docx")).thenReturn(templatePath);
        when(docxTemplateEngine.extractPlaceholders(templatePath)).thenReturn(Set.of("{{CONTRACT_CONTRACT_DATE}}"));
        when(placeholderRegistry.knownCodes(WordPlaceholderRegistry.CONTRACT)).thenReturn(Set.of("{{CONTRACT_CONTRACT_DATE}}"));
        when(placeholderRegistry.fields(WordPlaceholderRegistry.CONTRACT)).thenReturn(List.of(
            new WordPlaceholderFieldDto(WordPlaceholderRegistry.CONTRACT, "Contract", "Contract Date", "{{CONTRACT_CONTRACT_DATE}}", "contracts", "contract_date", false, false)
        ));
        when(repository.findAccessibleContract(eq(1L), any())).thenReturn(Optional.of(Map.of(
            "contract_id", 1L,
            "contract_number", "C-001",
            "contract_date", LocalDate.of(2024, 9, 11)
        )));

        service.generate(new GenerateWordDocumentRequest(WordPlaceholderRegistry.CONTRACT, 1L, 10L), null);

        ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
        verify(docxTemplateEngine).merge(eq(templatePath), any(Path.class), values.capture());
        assertThat(values.getValue()).containsEntry("{{CONTRACT_CONTRACT_DATE}}", "11th Sep 2024");
    }
}
