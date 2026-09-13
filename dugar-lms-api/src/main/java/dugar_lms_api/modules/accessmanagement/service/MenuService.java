package dugar_lms_api.modules.accessmanagement.service;

import dugar_lms_api.modules.accessmanagement.dto.MenuResponse;
import dugar_lms_api.modules.accessmanagement.model.Menu;
import dugar_lms_api.modules.accessmanagement.repository.MenuRepository;
import dugar_lms_api.modules.accessmanagement.repository.RolePermissionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MenuService {

    private final MenuRepository menuRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public MenuService(MenuRepository menuRepository, RolePermissionRepository rolePermissionRepository) {
        this.menuRepository = menuRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public List<MenuResponse> getMenuTreeByRoleId(Long roleId) {
        List<Long> menuIds = getViewableMenuIds(roleId);
        return getMenuTreeByMenuIds(menuIds);
    }

    public List<MenuResponse> getMenuTreeByRoleIds(List<Long> roleIds) {
        List<Long> menuIds = rolePermissionRepository.findViewableMenuIdsByRoleIds(roleIds);
        return getMenuTreeByMenuIds(menuIds);
    }

    private List<MenuResponse> getMenuTreeByMenuIds(List<Long> menuIds) {
        List<Menu> menus = loadMenus(menuIds);
        List<Menu> allMenus = loadMissingParents(menus);
        Map<Long, MenuResponse> menuResponseMap = convertToMenuResponses(allMenus);

        buildTree(allMenus, menuResponseMap);
        return findRootMenus(allMenus, menuResponseMap);
    }

    private List<Long> getViewableMenuIds(Long roleId) {
        return rolePermissionRepository.findViewableMenuIdsByRoleId(roleId);
    }

    private List<Menu> loadMenus(List<Long> menuIds) {
        return menuRepository.findActiveVisibleByIds(menuIds);
    }

    private List<Menu> loadMissingParents(List<Menu> menus) {
        if (menus == null || menus.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Menu> menuMap = new LinkedHashMap<>();
        for (Menu menu : menus) {
            menuMap.put(menu.getMenuId(), menu);
        }

        boolean addedParent;
        do {
            addedParent = false;

            List<Menu> currentMenus = new ArrayList<>(menuMap.values());
            for (Menu menu : currentMenus) {
                Long parentId = menu.getParentId();
                if (parentId == null || menuMap.containsKey(parentId)) {
                    continue;
                }

                Optional<Menu> parentMenuOptional = menuRepository.findById(parentId);
                if (parentMenuOptional.isEmpty()) {
                    continue;
                }

                Menu parentMenu = parentMenuOptional.get();
                if (!Boolean.TRUE.equals(parentMenu.getIsActive()) || !Boolean.TRUE.equals(parentMenu.getIsVisible())) {
                    continue;
                }

                menuMap.put(parentMenu.getMenuId(), parentMenu);
                addedParent = true;
            }
        } while (addedParent);

        return new ArrayList<>(menuMap.values());
    }

    private Map<Long, MenuResponse> convertToMenuResponses(List<Menu> menus) {
        Map<Long, MenuResponse> responseMap = new LinkedHashMap<>();
        for (Menu menu : menus) {
            MenuResponse response = MenuResponse.builder()
                .menuId(menu.getMenuId())
                .menuName(menu.getMenuName())
                .menuCode(menu.getMenuCode())
                .menuType(menu.getMenuType())
                .urlPath(menu.getUrlPath())
                .displayOrder(menu.getDisplayOrder())
                .icon(menu.getIcon())
                .build();
            responseMap.put(menu.getMenuId(), response);
        }
        return responseMap;
    }

    private void buildTree(List<Menu> menus, Map<Long, MenuResponse> menuResponseMap) {
        for (Menu menu : menus) {
            Long parentId = menu.getParentId();
            if (parentId == null) {
                continue;
            }

            MenuResponse child = menuResponseMap.get(menu.getMenuId());
            MenuResponse parent = menuResponseMap.get(parentId);
            if (child != null && parent != null) {
                parent.getChildren().add(child);
            }
        }
    }

    private List<MenuResponse> findRootMenus(List<Menu> menus, Map<Long, MenuResponse> menuResponseMap) {
        List<MenuResponse> rootMenus = new ArrayList<>();
        for (Menu menu : menus) {
            if (menu.getParentId() == null) {
                MenuResponse root = menuResponseMap.get(menu.getMenuId());
                if (root != null) {
                    rootMenus.add(root);
                }
            }
        }
        return rootMenus;
    }
}
