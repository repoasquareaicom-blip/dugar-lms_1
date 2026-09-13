package dugar_lms_api.modules.reports;

import org.springframework.security.core.Authentication;

import java.util.Locale;
import java.util.Map;

public record ReportAccessScope(boolean fullAccess, Long userId, String userGroup) {

    public ReportAccessScope(boolean restricted) {
        this(!restricted, null, restricted ? "user" : "admin");
    }

    public static ReportAccessScope from(Authentication authentication) {
        if (authentication == null) {
            return new ReportAccessScope(true, null, "admin");
        }

        String group = null;
        Long userId = null;
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object value = details.get("userGroup");
            if (value != null) {
                group = String.valueOf(value);
            }
            Object id = details.get("userId");
            if (id instanceof Number number) {
                userId = number.longValue();
            } else if (id != null) {
                try {
                    userId = Long.valueOf(String.valueOf(id));
                } catch (NumberFormatException ignored) {
                    userId = null;
                }
            }
        }

        String normalizedGroup = normalize(group);
        return new ReportAccessScope("admin".equals(normalizedGroup), userId, normalizedGroup);
    }

    public boolean restrictedToUserGroup() {
        return !fullAccess;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
