package dugar_lms_api.modules.wordtemplates.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WordTemplateMetadataRequest(
    @NotBlank(message = "Template Name is required.")
    @Size(max = 150, message = "Template Name must be 150 characters or less.")
    String templateName,

    Integer displayOrder,
    Boolean isActive,
    @NotBlank String languageCode,
    @Size(max = 150) String documentKey
) {
}
