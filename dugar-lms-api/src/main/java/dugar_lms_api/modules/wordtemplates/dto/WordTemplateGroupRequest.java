package dugar_lms_api.modules.wordtemplates.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WordTemplateGroupRequest(
    @NotBlank(message = "Group Name is required.")
    @Size(max = 150, message = "Group Name must be 150 characters or less.")
    String groupName,

    @Size(max = 500, message = "Description must be 500 characters or less.")
    String description,

    @Size(max = 50, message = "Data Source must be 50 characters or less.")
    String dataSource,

    Integer displayOrder,
    Boolean isActive
) {
}
