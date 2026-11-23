package dugar_lms_api.modules.wordtemplates.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateWordDocumentRequest(
    @NotBlank(message = "Data Source is required.")
    String dataSource,

    @NotNull(message = "Contract is required.")
    Long contractId,

    @NotNull(message = "Document Template is required.")
    Long templateId,
    String languageCode,
    java.util.UUID previewId
) {
    public GenerateWordDocumentRequest(String dataSource, Long contractId, Long templateId) { this(dataSource,contractId,templateId,"en",null); }
    public GenerateWordDocumentRequest { if(languageCode==null||languageCode.isBlank())languageCode="en"; }
}
