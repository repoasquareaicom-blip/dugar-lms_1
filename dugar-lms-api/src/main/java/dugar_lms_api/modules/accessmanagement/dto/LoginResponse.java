package dugar_lms_api.modules.accessmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private boolean success;

    private String message;

    private String token;

    private UserInfoResponse user;

    @Builder.Default
    private List<MenuResponse> menus = new ArrayList<>();
}