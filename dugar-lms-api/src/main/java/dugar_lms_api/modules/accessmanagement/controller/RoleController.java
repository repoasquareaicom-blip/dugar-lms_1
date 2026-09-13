package dugar_lms_api.modules.accessmanagement.controller;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accessmanagement.dto.RoleRequest;
import dugar_lms_api.modules.accessmanagement.model.Role;
import dugar_lms_api.modules.accessmanagement.service.RoleCriteria;
import dugar_lms_api.modules.accessmanagement.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/access-management/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<Role>> find(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean isActive,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sortColumn,
        @RequestParam(required = false) String sortDirection
    ) {
        return ResponseEntity.ok(service.find(new RoleCriteria(keyword, isActive, page, size, sortColumn, sortDirection)));
    }

    @PostMapping
    public ResponseEntity<Role> create(@Valid @RequestBody RoleRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.create(request, userId(authentication)));
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<Role> update(
        @PathVariable Long roleId,
        @Valid @RequestBody RoleRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.update(roleId, request, userId(authentication)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> duplicateRole(Exception exception) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("message", "Role name or role code already exists."));
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
