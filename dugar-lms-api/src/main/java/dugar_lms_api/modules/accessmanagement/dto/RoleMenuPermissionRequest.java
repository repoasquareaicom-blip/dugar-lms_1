package dugar_lms_api.modules.accessmanagement.dto;

import java.util.List;

public record RoleMenuPermissionRequest(
    List<Long> menuIds
) {
}
