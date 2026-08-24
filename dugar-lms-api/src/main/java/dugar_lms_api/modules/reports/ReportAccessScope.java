package dugar_lms_api.modules.reports;

import org.springframework.security.core.Authentication;

import java.util.Locale;
import java.util.Map;

public record ReportAccessScope(boolean restrictedToUserGroup) {

    public static ReportAccessScope from(Authentication authentication) {
        String group = null;
        String roleCode = null;
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object value = details.get("userGroup");
            if (value != null) {
                group = String.valueOf(value);
            }
            Object roleValue = details.get("roleCode");
            if (roleValue != null) {
                roleCode = String.valueOf(roleValue);
            }
        }
        if (group == null || group.isBlank()) {
            String normalizedRole = normalize(roleCode);
            group = normalizedRole.startsWith("super_admin") || normalizedRole.startsWith("admin") ? "admin" : "user";
        }
        return new ReportAccessScope("user".equals(normalize(group)));
    }

    private static String normalize(String value) {
        return value == null ? "admin" : value.trim().toLowerCase(Locale.ROOT);
    }
}
