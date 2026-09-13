package dugar_lms_api.modules.accessmanagement.service;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accessmanagement.dto.RoleRequest;
import dugar_lms_api.modules.accessmanagement.model.Role;
import dugar_lms_api.modules.accessmanagement.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    public static final String SUPER_ADMIN_CODE = "SUPER_ADMIN_1";

    private final RoleRepository repository;

    public RoleService(RoleRepository repository) {
        this.repository = repository;
    }

    public PageResponse<Role> find(RoleCriteria criteria) {
        return repository.find(criteria);
    }

    @Transactional
    public Role create(RoleRequest request, Long userId) {
        validateDuplicateName(request.roleName(), null);
        validateDuplicateCode(request.roleCode(), null);
        return repository.create(request, userId);
    }

    @Transactional
    public Role update(Long roleId, RoleRequest request, Long userId) {
        Role existing = repository.findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found."));

        if (isSuperAdmin(existing)) {
            if (!SUPER_ADMIN_CODE.equalsIgnoreCase(clean(request.roleCode()))) {
                throw new IllegalArgumentException("SUPER_ADMIN role code cannot be changed.");
            }
            if (Boolean.FALSE.equals(request.isActive())) {
                throw new IllegalArgumentException("SUPER_ADMIN role cannot be deactivated.");
            }
        }

        validateDuplicateName(request.roleName(), roleId);
        validateDuplicateCode(request.roleCode(), roleId);
        return repository.update(roleId, request, userId);
    }

    private void validateDuplicateName(String roleName, Long excludeRoleId) {
        if (repository.existsByName(roleName, excludeRoleId)) {
            throw new IllegalArgumentException("Role name already exists.");
        }
    }

    private void validateDuplicateCode(String roleCode, Long excludeRoleId) {
        if (repository.existsByCode(roleCode, excludeRoleId)) {
            throw new IllegalArgumentException("Role code already exists.");
        }
    }

    private boolean isSuperAdmin(Role role) {
        return role != null && SUPER_ADMIN_CODE.equalsIgnoreCase(role.getRoleCode());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
