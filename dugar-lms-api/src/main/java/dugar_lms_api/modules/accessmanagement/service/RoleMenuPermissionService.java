package dugar_lms_api.modules.accessmanagement.service;

import dugar_lms_api.modules.accessmanagement.dto.MenuResponse;
import dugar_lms_api.modules.accessmanagement.dto.RoleMenuPermissionRequest;
import dugar_lms_api.modules.accessmanagement.dto.RoleMenuPermissionResponse;
import dugar_lms_api.modules.accessmanagement.model.Menu;
import dugar_lms_api.modules.accessmanagement.model.Role;
import dugar_lms_api.modules.accessmanagement.repository.MenuRepository;
import dugar_lms_api.modules.accessmanagement.repository.RolePermissionRepository;
import dugar_lms_api.modules.accessmanagement.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoleMenuPermissionService {

    private final MenuRepository menuRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;

    public RoleMenuPermissionService(
        MenuRepository menuRepository,
        RolePermissionRepository rolePermissionRepository,
        RoleRepository roleRepository
    ) {
        this.menuRepository = menuRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleRepository = roleRepository;
    }

    public List<MenuResponse> getActiveMenuTree() {
        List<Menu> menus = menuRepository.findAllActiveVisible();
        Map<Long, MenuResponse> responseMap = new LinkedHashMap<>();
        for (Menu menu : menus) {
            responseMap.put(menu.getMenuId(), toResponse(menu));
        }

        List<MenuResponse> roots = new ArrayList<>();
        for (Menu menu : menus) {
            MenuResponse response = responseMap.get(menu.getMenuId());
            if (menu.getParentId() == null) {
                roots.add(response);
                continue;
            }

            MenuResponse parent = responseMap.get(menu.getParentId());
            if (parent != null) {
                parent.getChildren().add(response);
            }
        }
        return roots;
    }

    public RoleMenuPermissionResponse getPermissions(Long roleId) {
        requireActiveRole(roleId);
        return new RoleMenuPermissionResponse(roleId, rolePermissionRepository.findViewableMenuIdsByRoleId(roleId));
    }

    @Transactional
    public RoleMenuPermissionResponse save(Long roleId, RoleMenuPermissionRequest request, Long userId) {
        Role role = requireActiveRole(roleId);
        if (RoleService.SUPER_ADMIN_CODE.equalsIgnoreCase(role.getRoleCode())) {
            throw new IllegalArgumentException("SUPER_ADMIN permissions cannot be changed.");
        }
        Set<Long> normalizedMenuIds = normalizeMenuIds(request == null ? null : request.menuIds());
        rolePermissionRepository.replaceViewPermissions(roleId, new ArrayList<>(normalizedMenuIds), userId);
        return new RoleMenuPermissionResponse(roleId, rolePermissionRepository.findViewableMenuIdsByRoleId(roleId));
    }

    private Role requireActiveRole(Long roleId) {
        return roleRepository.findActiveById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found or inactive."));
    }

    private Set<Long> normalizeMenuIds(List<Long> requestedMenuIds) {
        List<Menu> activeMenus = menuRepository.findAllActiveVisible();
        Map<Long, Menu> activeMenuMap = new LinkedHashMap<>();
        for (Menu menu : activeMenus) {
            activeMenuMap.put(menu.getMenuId(), menu);
        }

        Set<Long> normalized = new LinkedHashSet<>();
        if (requestedMenuIds == null) {
            return normalized;
        }

        for (Long menuId : requestedMenuIds) {
            addMenuAndParents(menuId, activeMenuMap, normalized);
        }
        return normalized;
    }

    private void addMenuAndParents(Long menuId, Map<Long, Menu> activeMenuMap, Set<Long> normalized) {
        if (menuId == null || !activeMenuMap.containsKey(menuId)) {
            return;
        }
        Menu menu = activeMenuMap.get(menuId);
        Long parentId = menu.getParentId();
        if (parentId != null) {
            addMenuAndParents(parentId, activeMenuMap, normalized);
        }
        normalized.add(menuId);
    }

    private MenuResponse toResponse(Menu menu) {
        return MenuResponse.builder()
            .menuId(menu.getMenuId())
            .menuName(menu.getMenuName())
            .menuCode(menu.getMenuCode())
            .menuType(menu.getMenuType())
            .urlPath(menu.getUrlPath())
            .displayOrder(menu.getDisplayOrder())
            .icon(menu.getIcon())
            .build();
    }
}
