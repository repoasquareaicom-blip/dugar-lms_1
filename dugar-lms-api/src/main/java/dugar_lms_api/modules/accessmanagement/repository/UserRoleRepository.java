package dugar_lms_api.modules.accessmanagement.repository;

import dugar_lms_api.modules.accessmanagement.model.Role;
import dugar_lms_api.modules.accessmanagement.mapper.RoleRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class UserRoleRepository {

    private static final String BASE_ROLE_COLUMNS = """
        SELECT
            roles.role_id,
            roles.role_name,
            roles.role_code,
            roles.is_active,
            roles.created_at,
            roles.updated_at
        FROM roles
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserRoleRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Role> findActiveRolesByUserId(Long userId) {
        return jdbcTemplate.query(
            BASE_ROLE_COLUMNS + """
            JOIN user_roles
              ON user_roles.role_id = roles.role_id
            WHERE user_roles.user_id = :userId
              AND roles.is_active = TRUE
            ORDER BY roles.role_name
            """,
            new MapSqlParameterSource("userId", userId),
            new RoleRowMapper()
        );
    }

    public List<Long> findRoleIdsByUserId(Long userId) {
        return jdbcTemplate.queryForList(
            """
            SELECT role_id
            FROM user_roles
            WHERE user_id = :userId
            ORDER BY role_id
            """,
            new MapSqlParameterSource("userId", userId),
            Long.class
        );
    }

    public List<String> findRoleNamesByUserId(Long userId) {
        return jdbcTemplate.queryForList(
            """
            SELECT roles.role_name
            FROM roles
            JOIN user_roles
              ON user_roles.role_id = roles.role_id
            WHERE user_roles.user_id = :userId
            ORDER BY roles.role_name
            """,
            new MapSqlParameterSource("userId", userId),
            String.class
        );
    }

    public void replaceUserRoles(Long userId, List<Long> roleIds) {
        jdbcTemplate.update(
            "DELETE FROM user_roles WHERE user_id = :userId",
            new MapSqlParameterSource("userId", userId)
        );

        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] batch = roleIds.stream()
            .distinct()
            .map(roleId -> new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("roleId", roleId))
            .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO user_roles (user_id, role_id)
            VALUES (:userId, :roleId)
            ON CONFLICT (user_id, role_id) DO NOTHING
            """,
            batch
        );
    }

    public void ensureUserRole(Long userId, Long roleId) {
        jdbcTemplate.update(
            """
            INSERT INTO user_roles (user_id, role_id)
            VALUES (:userId, :roleId)
            ON CONFLICT (user_id, role_id) DO NOTHING
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("roleId", roleId)
        );
    }

    public List<Role> findActiveRolesByIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        return jdbcTemplate.query(
            BASE_ROLE_COLUMNS + """
            WHERE roles.role_id IN (:roleIds)
              AND roles.is_active = TRUE
            ORDER BY roles.role_name
            """,
            new MapSqlParameterSource("roleIds", roleIds),
            new RoleRowMapper()
        );
    }
}
