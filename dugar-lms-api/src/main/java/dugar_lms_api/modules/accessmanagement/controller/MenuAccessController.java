package dugar_lms_api.modules.accessmanagement.controller;

import dugar_lms_api.modules.accessmanagement.dto.MenuAccessResponse;
import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/access-management/menu-access")
public class MenuAccessController {

    private final MenuAccessService menuAccessService;

    public MenuAccessController(MenuAccessService menuAccessService) {
        this.menuAccessService = menuAccessService;
    }

    @GetMapping("/{menuCode}")
    public ResponseEntity<MenuAccessResponse> canView(
        @PathVariable String menuCode,
        Authentication authentication
    ) {
        if (!menuAccessService.canView(menuCode, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Menu access denied.");
        }
        return ResponseEntity.ok(new MenuAccessResponse(menuCode, true));
    }
}
