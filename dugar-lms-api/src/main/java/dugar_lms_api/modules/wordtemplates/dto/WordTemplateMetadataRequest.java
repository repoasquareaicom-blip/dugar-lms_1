package dugar_lms_api.modules.wordtemplates.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WordTemplateMetadataRequest(
    @NotNull(message = "Template Group is required.")
    Long groupId,

    @NotBlank(message = "Template Name is required.")
    @Size(max = 150, message = "Template Name must be 150 characters or less.")
    String templateName,

    Integer displayOrder,
    Boolean isActive
) {
}
