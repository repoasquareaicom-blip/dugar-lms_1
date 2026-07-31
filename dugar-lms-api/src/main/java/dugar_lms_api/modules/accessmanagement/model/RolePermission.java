package dugar_lms_api.modules.accessmanagement.model;

import dugar_lms_api.common.model.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RolePermission extends BaseEntity {

    private Long permissionId;

    private Long roleId;

    private Long menuId;

    private Boolean canView = false;

    private Boolean canAdd = false;

    private Boolean canEdit = false;

    private Boolean canDelete = false;

    private Long createdBy;

    private Long updatedBy;
}