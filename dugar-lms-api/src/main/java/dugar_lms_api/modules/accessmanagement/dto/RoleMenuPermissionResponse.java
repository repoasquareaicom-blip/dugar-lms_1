package dugar_lms_api.modules.accessmanagement.dto;

import java.util.List;

public record RoleMenuPermissionResponse(
    Long roleId,
    List<Long> menuIds
) {
}
