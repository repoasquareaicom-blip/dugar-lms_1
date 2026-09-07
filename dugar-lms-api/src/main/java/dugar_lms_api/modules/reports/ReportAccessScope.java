package dugar_lms_api.modules.reports;

import org.springframework.security.core.Authentication;

import java.util.Locale;
import java.util.Map;

public record ReportAccessScope(boolean restrictedToUserGroup, String userGroup) {

    public ReportAccessScope(boolean restrictedToUserGroup) {
        this(restrictedToUserGroup, restrictedToUserGroup ? "user" : "admin");
    }

    public static ReportAccessScope from(Authentication authentication) {
        String group = null;
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object value = details.get("userGroup");
            if (value != null) {
                group = String.valueOf(value);
            }
        }

        String normalizedGroup = normalize(group);
        return new ReportAccessScope(!"admin".equals(normalizedGroup), normalizedGroup);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
