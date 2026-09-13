package dugar_lms_api.modules.accessmanagement.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UserManagementRequest(
    @NotBlank
    String fullName,
    @NotBlank
    String username,
    String password,
    @NotBlank
    String userType,
    String contractNumber,
    Long departmentMenuId,
    Boolean isActive,
    List<Long> roleIds,
    List<Long> menuIds,
    List<String> areaCodes
) {
}
