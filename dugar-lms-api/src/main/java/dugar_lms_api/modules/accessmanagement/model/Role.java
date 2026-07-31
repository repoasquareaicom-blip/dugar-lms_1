package dugar_lms_api.modules.accessmanagement.model;

import dugar_lms_api.common.model.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Role extends BaseEntity {

    private Long roleId;

    private String roleName;

    private String roleCode;

    private Boolean isActive = true;
}