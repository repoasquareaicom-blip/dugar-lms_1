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
public class MenuResponse {

    private Long menuId;

    private String menuName;

    private String menuCode;

    private String urlPath;

    private String icon;

    @Builder.Default
    private List<MenuResponse> children = new ArrayList<>();
}