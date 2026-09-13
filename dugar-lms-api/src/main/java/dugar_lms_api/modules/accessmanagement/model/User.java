package dugar_lms_api.modules.accessmanagement.model;

import dugar_lms_api.common.model.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    private Long userId;

    private String username;

    private String passwordHash;

    private String fullName;

    private String emailId;

    private Long roleId;

    private String userGroup = "admin";

    private String userType = "USER";

    private Boolean isActive = true;

    private LocalDateTime lastLoginAt;
}
