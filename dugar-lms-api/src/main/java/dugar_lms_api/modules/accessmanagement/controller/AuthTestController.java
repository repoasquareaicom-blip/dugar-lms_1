package dugar_lms_api.modules.accessmanagement.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class AuthTestController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String username = authentication != null ? String.valueOf(authentication.getPrincipal()) : null;
        Long userId = null;
        Long roleId = null;
        String roleCode = null;

        Object details = authentication != null ? authentication.getDetails() : null;
        if (details instanceof Map<?, ?> detailsMap) {
            userId = toLong(detailsMap.get("userId"));
            roleId = toLong(detailsMap.get("roleId"));
            Object roleCodeValue = detailsMap.get("roleCode");
            roleCode = roleCodeValue != null ? String.valueOf(roleCodeValue) : null;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticated", true);
        response.put("username", username);
        response.put("userId", userId);
        response.put("roleId", roleId);
        response.put("roleCode", roleCode);

        return ResponseEntity.ok(response);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}