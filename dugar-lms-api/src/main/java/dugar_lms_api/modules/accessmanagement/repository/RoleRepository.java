package dugar_lms_api.modules.accessmanagement.repository;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accessmanagement.dto.RoleRequest;
import dugar_lms_api.modules.accessmanagement.mapper.RoleRowMapper;
import dugar_lms_api.modules.accessmanagement.model.Role;
import dugar_lms_api.modules.accessmanagement.service.RoleCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepository {

    private static final String BASE_ROLE_COLUMNS = """
        SELECT
            role_id,
            role_name,
            role_code,
            is_active,
            created_at,
            updated_at
        FROM roles
        """;

    private static final String FIND_BY_ID_SQL = BASE_ROLE_COLUMNS + """
        WHERE role_id = :roleId
        """;

    private static final String FIND_ACTIVE_BY_ID_SQL = BASE_ROLE_COLUMNS + """
        WHERE role_id = :roleId
          AND is_active = TRUE
        """;

    private static final String FIND_ACTIVE_BY_CODE_SQL = BASE_ROLE_COLUMNS + """
        WHERE LOWER(role_code) = LOWER(:roleCode)
          AND is_active = TRUE
        """;

    private static final String FIND_ALL_ACTIVE_SQL = BASE_ROLE_COLUMNS + """
        WHERE is_active = TRUE
        ORDER BY role_name
        """;

    private static final String FIND_GENERATED_USER_ROLE_SQL = BASE_ROLE_COLUMNS + """
        WHERE role_code = :roleCode
          AND is_active = TRUE
        """;

    private static final String EXISTS_BY_CODE_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM roles
            WHERE LOWER(role_code) = LOWER(:roleCode)
        )
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PageResponse<Role> find(RoleCriteria criteria) {
        int page = criteria.page() == null || criteria.page() < 0 ? 0 : criteria.page();
        int size = criteria.size() == null || criteria.size() <= 0 ? 25 : Math.min(criteria.size(), 200);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", size)
            .addValue("offset", page * size);

        String where = where(criteria, params);
        long total = namedParameterJdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles " + where, params, Long.class);
        List<Role> content = namedParameterJdbcTemplate.query(
            BASE_ROLE_COLUMNS + where + " ORDER BY " + sortColumn(criteria.sortColumn()) + " " + sortDirection(criteria.sortDirection()) + " LIMIT :limit OFFSET :offset",
            params,
            new RoleRowMapper()
        );
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(
            content,
            page,
            size,
            total,
            totalPages,
            page == 0,
            page >= Math.max(totalPages - 1, 0),
            content.size()
        );
    }

    public Optional<Role> findById(Long roleId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleId", roleId);

        List<Role> roles = namedParameterJdbcTemplate.query(FIND_BY_ID_SQL, params, new RoleRowMapper());
        return roles.stream().findFirst();
    }

    public Optional<Role> findActiveById(Long roleId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleId", roleId);

        List<Role> roles = namedParameterJdbcTemplate.query(FIND_ACTIVE_BY_ID_SQL, params, new RoleRowMapper());
        return roles.stream().findFirst();
    }

    public Optional<Role> findActiveByCode(String roleCode) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleCode", roleCode);

        List<Role> roles = namedParameterJdbcTemplate.query(FIND_ACTIVE_BY_CODE_SQL, params, new RoleRowMapper());
        return roles.stream().findFirst();
    }

    public List<Role> findAllActive() {
        return namedParameterJdbcTemplate.query(FIND_ALL_ACTIVE_SQL, new RoleRowMapper());
    }

    public Optional<Role> findGeneratedUserRole(Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleCode", generatedUserRoleCode(userId));

        List<Role> roles = namedParameterJdbcTemplate.query(FIND_GENERATED_USER_ROLE_SQL, params, new RoleRowMapper());
        return roles.stream().findFirst();
    }

    public Role createGeneratedUserRole(Long userId) {
        String roleCode = generatedUserRoleCode(userId);
        return namedParameterJdbcTemplate.queryForObject(
            """
            INSERT INTO roles (
                role_name,
                role_code,
                is_active
            )
            VALUES (
                :roleName,
                :roleCode,
                TRUE
            )
            ON CONFLICT (role_code) DO UPDATE
            SET
                role_name = EXCLUDED.role_name,
                is_active = TRUE
            RETURNING role_id, role_name, role_code, is_active, created_at, updated_at
            """,
            new MapSqlParameterSource()
                .addValue("roleName", roleCode)
                .addValue("roleCode", roleCode),
            new RoleRowMapper()
        );
    }

    public boolean existsByCode(String roleCode) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleCode", roleCode);

        Boolean exists = namedParameterJdbcTemplate.queryForObject(EXISTS_BY_CODE_SQL, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public boolean existsByName(String roleName, Long excludeRoleId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleName", cleanName(roleName));

        Boolean exists = namedParameterJdbcTemplate.queryForObject(
            excludeRoleId == null
                ? """
                SELECT EXISTS (
                    SELECT 1
                    FROM roles
                    WHERE LOWER(role_name) = LOWER(:roleName)
                )
                """
                : """
                SELECT EXISTS (
                    SELECT 1
                    FROM roles
                    WHERE LOWER(role_name) = LOWER(:roleName)
                      AND role_id <> :excludeRoleId
                )
                """,
            excludeRoleId == null ? params : params.addValue("excludeRoleId", excludeRoleId),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public boolean existsByCode(String roleCode, Long excludeRoleId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleCode", cleanCode(roleCode));

        Boolean exists = namedParameterJdbcTemplate.queryForObject(
            excludeRoleId == null
                ? """
                SELECT EXISTS (
                    SELECT 1
                    FROM roles
                    WHERE LOWER(role_code) = LOWER(:roleCode)
                )
                """
                : """
                SELECT EXISTS (
                    SELECT 1
                    FROM roles
                    WHERE LOWER(role_code) = LOWER(:roleCode)
                      AND role_id <> :excludeRoleId
                )
                """,
            excludeRoleId == null ? params : params.addValue("excludeRoleId", excludeRoleId),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public Role create(RoleRequest request, Long userId) {
        return namedParameterJdbcTemplate.queryForObject(
            """
            INSERT INTO roles (
                role_name,
                role_code,
                is_active
            )
            VALUES (
                :roleName,
                :roleCode,
                :isActive
            )
            RETURNING role_id, role_name, role_code, is_active, created_at, updated_at
            """,
            params(request, true).addValue("userId", userId),
            new RoleRowMapper()
        );
    }

    public Role update(Long roleId, RoleRequest request, Long userId) {
        return namedParameterJdbcTemplate.queryForObject(
            """
            UPDATE roles
            SET
                role_name = :roleName,
                role_code = :roleCode,
                is_active = COALESCE(:isActive, is_active)
            WHERE role_id = :roleId
            RETURNING role_id, role_name, role_code, is_active, created_at, updated_at
            """,
            params(request, false)
                .addValue("roleId", roleId)
                .addValue("userId", userId),
            new RoleRowMapper()
        );
    }

    private String where(RoleCriteria criteria, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder("""
             WHERE role_code NOT LIKE 'USER\\_%\\_PERMISSIONS' ESCAPE '\\'
            """);
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            where.append("""
                 AND (
                    role_name ILIKE :keyword
                    OR role_code ILIKE :keyword
                 )
                """);
            params.addValue("keyword", "%" + criteria.keyword().trim() + "%");
        }
        if (criteria.isActive() != null) {
            where.append(" AND is_active = :isActive");
            params.addValue("isActive", criteria.isActive());
        }
        return where.toString();
    }

    private MapSqlParameterSource params(RoleRequest request, boolean defaultActive) {
        Boolean isActive = request.isActive();
        if (defaultActive && isActive == null) {
            isActive = true;
        }
        return new MapSqlParameterSource()
            .addValue("roleName", cleanName(request.roleName()))
            .addValue("roleCode", cleanCode(request.roleCode()))
            .addValue("isActive", isActive);
    }

    private String sortColumn(String sortColumn) {
        if (sortColumn == null) {
            return "role_name";
        }
        return switch (sortColumn) {
            case "roleCode" -> "role_code";
            case "isActive" -> "is_active";
            case "updatedAt" -> "updated_at";
            default -> "role_name";
        };
    }

    private String sortDirection(String sortDirection) {
        return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
    }

    private String cleanName(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String cleanCode(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private String generatedUserRoleCode(Long userId) {
        return "USER_" + userId + "_PERMISSIONS";
    }
}
