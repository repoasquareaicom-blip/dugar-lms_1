package dugar_lms_api.modules.accessmanagement.service;

import dugar_lms_api.modules.accessmanagement.repository.RolePermissionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MenuAccessService {

    private final RolePermissionRepository rolePermissionRepository;

    public MenuAccessService(RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public boolean canView(String menuCode, Authentication authentication) {
        return rolePermissionRepository.canUserViewMenu(userId(authentication), menuCode);
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
