package dugar_lms_api.modules.accessmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(
    @NotBlank
    String roleName,
    @NotBlank
    String roleCode,
    Boolean isActive
) {
}
