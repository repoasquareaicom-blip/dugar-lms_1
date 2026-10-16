package dugar_lms_api.modules.wordtemplates.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateWordDocumentRequest(
    @NotBlank(message = "Data Source is required.")
    String dataSource,

    @NotNull(message = "Contract is required.")
    Long contractId,

    @NotNull(message = "Word Template is required.")
    Long templateId
) {
}
