package dugar_lms_api.modules.accessmanagement.controller;

import dugar_lms_api.modules.accessmanagement.dto.MenuResponse;
import dugar_lms_api.modules.accessmanagement.dto.RoleMenuPermissionRequest;
import dugar_lms_api.modules.accessmanagement.dto.RoleMenuPermissionResponse;
import dugar_lms_api.modules.accessmanagement.service.RoleMenuPermissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/access-management/role-menu-permissions")
public class RoleMenuPermissionController {

    private final RoleMenuPermissionService service;

    public RoleMenuPermissionController(RoleMenuPermissionService service) {
        this.service = service;
    }

    @GetMapping("/menus")
    public ResponseEntity<List<MenuResponse>> menus() {
        return ResponseEntity.ok(service.getActiveMenuTree());
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<RoleMenuPermissionResponse> permissions(@PathVariable Long roleId) {
        return ResponseEntity.ok(service.getPermissions(roleId));
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<RoleMenuPermissionResponse> save(
        @PathVariable Long roleId,
        @Valid @RequestBody RoleMenuPermissionRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.save(roleId, request, userId(authentication)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", exception.getMessage()));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            return null;
        }
        Object userId = details.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        if (userId == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(userId));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
