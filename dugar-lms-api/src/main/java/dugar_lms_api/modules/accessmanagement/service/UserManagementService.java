package dugar_lms_api.modules.accessmanagement.service;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accessmanagement.dto.ContractOptionDto;
import dugar_lms_api.modules.accessmanagement.dto.UserManagementDto;
import dugar_lms_api.modules.accessmanagement.dto.UserManagementRequest;
import dugar_lms_api.modules.accessmanagement.model.Role;
import dugar_lms_api.modules.accessmanagement.model.User;
import dugar_lms_api.modules.accessmanagement.repository.MenuRepository;
import dugar_lms_api.modules.accessmanagement.repository.RoleRepository;
import dugar_lms_api.modules.accessmanagement.repository.RolePermissionRepository;
import dugar_lms_api.modules.accessmanagement.repository.UserAreaRepository;
import dugar_lms_api.modules.accessmanagement.repository.UserContractRepository;
import dugar_lms_api.modules.accessmanagement.repository.UserRepository;
import dugar_lms_api.modules.accessmanagement.repository.UserRoleRepository;
import dugar_lms_api.modules.contracts.repository.ContractListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserManagementService {

    private static final String USER_TYPE_USER = "USER";
    private static final String USER_TYPE_BRANCH = "BRANCH";
    private static final String USER_TYPE_STATE = "STATE";
    private static final String USER_TYPE_CUSTOMER = "CUSTOMER";
    private static final String BRANCH_ROLE_CODE = "BRANCH";
    private static final String CUSTOMER_ROLE_CODE = "CUSTOMER";

    private final ContractListRepository contractListRepository;
    private final MenuRepository menuRepository;
    private final PasswordService passwordService;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;
    private final UserAreaRepository userAreaRepository;
    private final UserContractRepository userContractRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public UserManagementService(
        ContractListRepository contractListRepository,
        MenuRepository menuRepository,
        PasswordService passwordService,
        RolePermissionRepository rolePermissionRepository,
        RoleRepository roleRepository,
        UserAreaRepository userAreaRepository,
        UserContractRepository userContractRepository,
        UserRepository userRepository,
        UserRoleRepository userRoleRepository
    ) {
        this.contractListRepository = contractListRepository;
        this.menuRepository = menuRepository;
        this.passwordService = passwordService;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleRepository = roleRepository;
        this.userAreaRepository = userAreaRepository;
        this.userContractRepository = userContractRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    public PageResponse<UserManagementDto> find(UserManagementCriteria criteria) {
        PageResponse<User> users = userRepository.find(criteria);
        List<UserManagementDto> content = users.content().stream()
            .map(this::toDto)
            .toList();

        return new PageResponse<>(
            content,
            users.page(),
            users.size(),
            users.totalElements(),
            users.totalPages(),
            users.first(),
            users.last(),
            content.size()
        );
    }

    @Transactional
    public UserManagementDto create(UserManagementRequest request) {
        validateRequest(request, true, null);
        if (isUser(request)) {
            Long userId = userRepository.nextUserId();
            Role generatedRole = roleRepository.createGeneratedUserRole(userId);
            User user = userRepository.createWithId(userId, request, passwordService.encode(request.password()), generatedRole.getRoleId());
            syncUserMenuPermissions(userId, generatedRole, request.departmentMenuId(), request.menuIds());
            clearNonUserScopeMappings(userId);
            return toDto(userRepository.findById(userId).orElse(user));
        }

        List<Long> roleIds = resolveRoleIds(request);
        Long primaryRoleId = roleIds.get(0);
        User user = userRepository.create(request, passwordService.encode(request.password()), primaryRoleId);
        syncMappings(user.getUserId(), request, roleIds);
        return toDto(userRepository.findById(user.getUserId()).orElse(user));
    }

    @Transactional
    public UserManagementDto update(Long userId, UserManagementRequest request) {
        userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));

        validateRequest(request, false, userId);
        if (isUser(request)) {
            Role generatedRole = ensureGeneratedUserRole(userId);
            String passwordHash = hasText(request.password()) ? passwordService.encode(request.password()) : null;
            User user = userRepository.update(userId, request, passwordHash, generatedRole.getRoleId());
            syncUserMenuPermissions(userId, generatedRole, request.departmentMenuId(), request.menuIds());
            clearNonUserScopeMappings(userId);
            return toDto(user);
        }

        List<Long> roleIds = resolveRoleIds(request);
        Long primaryRoleId = roleIds.get(0);
        String passwordHash = hasText(request.password()) ? passwordService.encode(request.password()) : null;

        User user = userRepository.update(userId, request, passwordHash, primaryRoleId);
        syncMappings(userId, request, roleIds);
        return toDto(user);
    }

    public List<ContractOptionDto> activeContracts(String keyword, Integer limit) {
        return contractListRepository.findActiveContractOptions(keyword, limit == null ? 20 : limit);
    }

    private void validateRequest(UserManagementRequest request, boolean create, Long userId) {
        if (request == null) {
            throw new IllegalArgumentException("User request is required.");
        }
        if (userRepository.existsByUsername(request.username(), userId)) {
            throw new IllegalArgumentException("Login ID already exists.");
        }
        if (!isValidUserType(request.userType())) {
            throw new IllegalArgumentException("User type must be USER, BRANCH, STATE, or CUSTOMER.");
        }
        if (create && !hasText(request.password())) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (isUser(request)) {
            validateMenuIds(request.departmentMenuId(), request.menuIds());
            return;
        }
        if (isBranch(request)) {
            List<String> areaCodes = normalizeAreaCodes(request.areaCodes());
            if (areaCodes.size() != 1) {
                throw new IllegalArgumentException("Exactly one area is required for Branch users.");
            }
            validateActiveAreas(areaCodes);
            return;
        }
        if (isState(request)) {
            List<String> areaCodes = normalizeAreaCodes(request.areaCodes());
            if (areaCodes.isEmpty()) {
                throw new IllegalArgumentException("At least one area is required for State users.");
            }
            validateActiveAreas(areaCodes);
            return;
        }
        if (isCustomer(request)) {
            String contractNumber = clean(request.contractNumber());
            if (contractNumber == null) {
                throw new IllegalArgumentException("Contract number is required for Customer users.");
            }
            if (!contractListRepository.existsActiveContractNumber(contractNumber)) {
                throw new IllegalArgumentException("Selected contract must be active.");
            }
        }
    }

    private List<Long> resolveRoleIds(UserManagementRequest request) {
        if (isUser(request)) {
            return validateRoleIds(request.roleIds());
        }
        if (isBranch(request) || isState(request)) {
            Role branchRole = roleRepository.findActiveByCode(BRANCH_ROLE_CODE)
                .orElseThrow(() -> new IllegalArgumentException("Active BRANCH role was not found."));
            return List.of(branchRole.getRoleId());
        }
        if (isCustomer(request)) {
            Role customerRole = roleRepository.findActiveByCode(CUSTOMER_ROLE_CODE)
                .orElseThrow(() -> new IllegalArgumentException("Active CUSTOMER role was not found."));
            return List.of(customerRole.getRoleId());
        }
        throw new IllegalArgumentException("User type must be USER, BRANCH, STATE, or CUSTOMER.");
    }

    private List<Long> validateRoleIds(List<Long> requestedRoleIds) {
        Set<Long> deduped = new LinkedHashSet<>();
        if (requestedRoleIds != null) {
            requestedRoleIds.stream()
                .filter(roleId -> roleId != null)
                .forEach(deduped::add);
        }
        if (deduped.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required.");
        }

        List<Long> roleIds = new ArrayList<>(deduped);
        List<Role> activeRoles = userRoleRepository.findActiveRolesByIds(roleIds);
        if (activeRoles.size() != roleIds.size()) {
            throw new IllegalArgumentException("One or more selected roles are inactive or invalid.");
        }
        return roleIds;
    }

    private UserManagementDto toDto(User user) {
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(user.getUserId());
        if (roleIds.isEmpty() && user.getRoleId() != null) {
            roleIds = List.of(user.getRoleId());
        }
        List<Long> menuIds = selectedMenuIds(user, roleIds);
        Long departmentMenuId = resolveDepartmentMenuId(menuIds);

        return new UserManagementDto(
            user.getUserId(),
            user.getFullName(),
            user.getUsername(),
            user.getUserGroup(),
            user.getUserType(),
            userContractRepository.findContractNumberByUserId(user.getUserId()).orElse(null),
            departmentMenuId,
            userAreaRepository.findAreaCodesByUserId(user.getUserId()),
            user.getIsActive(),
            user.getRoleId(),
            roleIds,
            userRoleRepository.findRoleNamesByUserId(user.getUserId()),
            menuIds,
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getLastLoginAt()
        );
    }

    private Role ensureGeneratedUserRole(Long userId) {
        return roleRepository.findGeneratedUserRole(userId)
            .orElseGet(() -> roleRepository.createGeneratedUserRole(userId));
    }

    private void syncUserMenuPermissions(Long userId, Role generatedRole, Long departmentMenuId, List<Long> requestedMenuIds) {
        List<Long> menuIds = validateMenuIds(departmentMenuId, requestedMenuIds);
        rolePermissionRepository.replaceViewPermissions(generatedRole.getRoleId(), menuIds, userId);
        userRoleRepository.replaceUserRoles(userId, List.of(generatedRole.getRoleId()));
    }

    private List<Long> selectedMenuIds(User user, List<Long> roleIds) {
        if (!USER_TYPE_USER.equals(userType(user.getUserType()))) {
            return List.of();
        }

        return roleRepository.findGeneratedUserRole(user.getUserId())
            .map(role -> rolePermissionRepository.findViewableMenuIdsByRoleId(role.getRoleId()))
            .orElseGet(() -> rolePermissionRepository.findViewableMenuIdsByRoleIds(roleIds));
    }

    private List<Long> validateMenuIds(Long departmentMenuId, List<Long> requestedMenuIds) {
        if (departmentMenuId == null) {
            throw new IllegalArgumentException("Department is required for User permissions.");
        }

        List<dugar_lms_api.modules.accessmanagement.model.Menu> activeMenus = menuRepository.findAllActiveVisible();
        Map<Long, dugar_lms_api.modules.accessmanagement.model.Menu> menuMap = activeMenuMap(activeMenus);
        dugar_lms_api.modules.accessmanagement.model.Menu department = menuMap.get(departmentMenuId);
        if (department == null || department.getParentId() != null || isDashboardMenu(department)) {
            throw new IllegalArgumentException("Selected department is invalid.");
        }

        Set<Long> deduped = new LinkedHashSet<>();
        if (requestedMenuIds != null) {
            requestedMenuIds.stream()
                .filter(menuId -> menuId != null)
                .forEach(deduped::add);
        }
        if (deduped.isEmpty()) {
            throw new IllegalArgumentException("At least one menu permission is required.");
        }

        List<Long> menuIds = new ArrayList<>(deduped);
        for (Long menuId : menuIds) {
            dugar_lms_api.modules.accessmanagement.model.Menu menu = menuMap.get(menuId);
            if (menu == null) {
                throw new IllegalArgumentException("One or more selected menus are inactive or invalid.");
            }
            Long rootMenuId = rootMenuId(menu, menuMap);
            if (!departmentMenuId.equals(rootMenuId)) {
                throw new IllegalArgumentException("Selected menu permissions must belong to the selected department.");
            }
        }
        return menuIds;
    }

    private Long resolveDepartmentMenuId(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return null;
        }

        Map<Long, dugar_lms_api.modules.accessmanagement.model.Menu> menuMap = activeMenuMap(menuRepository.findAllActiveVisible());
        Set<Long> departmentIds = new LinkedHashSet<>();
        for (Long menuId : menuIds) {
            dugar_lms_api.modules.accessmanagement.model.Menu menu = menuMap.get(menuId);
            if (menu == null) {
                continue;
            }
            Long rootMenuId = rootMenuId(menu, menuMap);
            if (rootMenuId != null) {
                departmentIds.add(rootMenuId);
            }
        }
        return departmentIds.stream().findFirst().orElse(null);
    }

    private Map<Long, dugar_lms_api.modules.accessmanagement.model.Menu> activeMenuMap(List<dugar_lms_api.modules.accessmanagement.model.Menu> menus) {
        Map<Long, dugar_lms_api.modules.accessmanagement.model.Menu> map = new LinkedHashMap<>();
        for (dugar_lms_api.modules.accessmanagement.model.Menu menu : menus) {
            map.put(menu.getMenuId(), menu);
        }
        return map;
    }

    private Long rootMenuId(dugar_lms_api.modules.accessmanagement.model.Menu menu, Map<Long, dugar_lms_api.modules.accessmanagement.model.Menu> menuMap) {
        dugar_lms_api.modules.accessmanagement.model.Menu current = menu;
        while (current != null && current.getParentId() != null) {
            current = menuMap.get(current.getParentId());
        }
        return current == null ? null : current.getMenuId();
    }

    private boolean isDashboardMenu(dugar_lms_api.modules.accessmanagement.model.Menu menu) {
        String text = ((menu.getMenuName() == null ? "" : menu.getMenuName()) + " " + (menu.getMenuCode() == null ? "" : menu.getMenuCode())).toLowerCase();
        return text.contains("dashboard");
    }

    private boolean isValidUserType(String value) {
        String userType = userType(value);
        return USER_TYPE_USER.equals(userType)
            || USER_TYPE_BRANCH.equals(userType)
            || USER_TYPE_STATE.equals(userType)
            || USER_TYPE_CUSTOMER.equals(userType);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isCustomer(UserManagementRequest request) {
        return request != null && USER_TYPE_CUSTOMER.equals(userType(request.userType()));
    }

    private boolean isUser(UserManagementRequest request) {
        return request != null && USER_TYPE_USER.equals(userType(request.userType()));
    }

    private boolean isBranch(UserManagementRequest request) {
        return request != null && USER_TYPE_BRANCH.equals(userType(request.userType()));
    }

    private boolean isState(UserManagementRequest request) {
        return request != null && USER_TYPE_STATE.equals(userType(request.userType()));
    }

    private void syncMappings(Long userId, UserManagementRequest request, List<Long> roleIds) {
        userRoleRepository.replaceUserRoles(userId, roleIds);
        if (isCustomer(request)) {
            userContractRepository.replaceUserContract(userId, clean(request.contractNumber()));
            userAreaRepository.deleteByUserId(userId);
            return;
        }
        userContractRepository.deleteByUserId(userId);
        if (isBranch(request) || isState(request)) {
            userAreaRepository.replaceUserAreas(userId, normalizeAreaCodes(request.areaCodes()));
            return;
        }
        userAreaRepository.deleteByUserId(userId);
    }

    private void clearNonUserScopeMappings(Long userId) {
        userContractRepository.deleteByUserId(userId);
        userAreaRepository.deleteByUserId(userId);
    }

    private void validateActiveAreas(List<String> areaCodes) {
        for (String areaCode : areaCodes) {
            if (!contractListRepository.existsActiveAreaCode(areaCode)) {
                throw new IllegalArgumentException("Selected area must be active.");
            }
        }
    }

    private List<String> normalizeAreaCodes(List<String> areaCodes) {
        Set<String> deduped = new LinkedHashSet<>();
        if (areaCodes != null) {
            areaCodes.stream()
                .map(this::clean)
                .filter(value -> value != null)
                .forEach(deduped::add);
        }
        return new ArrayList<>(deduped);
    }

    private String userType(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
