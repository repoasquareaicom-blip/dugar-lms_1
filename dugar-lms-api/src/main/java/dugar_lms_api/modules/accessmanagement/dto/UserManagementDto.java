package dugar_lms_api.modules.accessmanagement.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserManagementDto(
    Long userId,
    String fullName,
    String username,
    String userGroup,
    String userType,
    String contractNumber,
    Long departmentMenuId,
    List<String> areaCodes,
    Boolean isActive,
    Long primaryRoleId,
    List<Long> roleIds,
    List<String> roleNames,
    List<Long> menuIds,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime lastLoginAt
) {
}
