package dugar_lms_api.modules.accessmanagement.model;

import dugar_lms_api.common.model.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Menu extends BaseEntity {

    private Long menuId;

    private String menuName;

    private String menuCode;

    private Long parentId;

    private String urlPath;

    private String icon;

    private Integer displayOrder = 0;

    private String menuType;

    private Boolean isActive = true;

    private Boolean isVisible = true;

    private Long createdBy;

    private Long updatedBy;
}