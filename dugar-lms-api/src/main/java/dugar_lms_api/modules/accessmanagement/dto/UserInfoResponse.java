package dugar_lms_api.modules.accessmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoResponse {

    private Long userId;

    private String username;

    private String fullName;

    private String emailId;

    private Long roleId;

    private String roleName;

    private String roleCode;

    private String userGroup;
}
