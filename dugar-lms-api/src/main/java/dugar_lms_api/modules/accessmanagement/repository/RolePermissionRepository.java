package dugar_lms_api.modules.accessmanagement.repository;

import dugar_lms_api.modules.accessmanagement.mapper.RolePermissionRowMapper;
import dugar_lms_api.modules.accessmanagement.model.RolePermission;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RolePermissionRepository {

    private static final String BASE_ROLE_PERMISSION_COLUMNS = """
        SELECT
            permission_id,
            role_id,
            menu_id,
            can_view,
            can_add,
            can_edit,
            can_delete,
            created_by,
            created_at,
            updated_by,
            updated_at
        FROM role_permissions
        """;

    private static final String FIND_BY_ROLE_ID_AND_MENU_ID_SQL = BASE_ROLE_PERMISSION_COLUMNS + """
        WHERE role_id = :roleId
        AND menu_id = :menuId
        """;

    private static final String FIND_BY_ROLE_ID_SQL = BASE_ROLE_PERMISSION_COLUMNS + """
        WHERE role_id = :roleId
        ORDER BY menu_id
        """;

    private static final String FIND_VIEWABLE_MENU_IDS_BY_ROLE_ID_SQL = """
        SELECT menu_id
        FROM role_permissions
        WHERE role_id = :roleId
        AND can_view = TRUE
        ORDER BY menu_id
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public RolePermissionRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<RolePermission> findByRoleIdAndMenuId(Long roleId, Long menuId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleId", roleId)
            .addValue("menuId", menuId);

        try {
            RolePermission rolePermission = namedParameterJdbcTemplate.queryForObject(
                FIND_BY_ROLE_ID_AND_MENU_ID_SQL,
                params,
                new RolePermissionRowMapper()
            );

            return Optional.ofNullable(rolePermission);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<RolePermission> findByRoleId(Long roleId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleId", roleId);

        return namedParameterJdbcTemplate.query(FIND_BY_ROLE_ID_SQL, params, new RolePermissionRowMapper());
    }

    public List<Long> findViewableMenuIdsByRoleId(Long roleId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleId", roleId);

        return namedParameterJdbcTemplate.queryForList(FIND_VIEWABLE_MENU_IDS_BY_ROLE_ID_SQL, params, Long.class);
    }

    public List<Long> findViewableMenuIdsByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }

        return namedParameterJdbcTemplate.queryForList(
            """
            SELECT DISTINCT menu_id
            FROM role_permissions
            WHERE role_id IN (:roleIds)
              AND can_view = TRUE
            ORDER BY menu_id
            """,
            new MapSqlParameterSource("roleIds", roleIds),
            Long.class
        );
    }

    public void replaceViewPermissions(Long roleId, List<Long> menuIds, Long userId) {
        MapSqlParameterSource deleteParams = new MapSqlParameterSource()
            .addValue("roleId", roleId);

        namedParameterJdbcTemplate.update(
            """
            DELETE FROM role_permissions
            WHERE role_id = :roleId
            """,
            deleteParams
        );

        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] batch = menuIds.stream()
            .distinct()
            .map(menuId -> new MapSqlParameterSource()
                .addValue("roleId", roleId)
                .addValue("menuId", menuId)
                .addValue("userId", userId))
            .toArray(MapSqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(
            """
            INSERT INTO role_permissions (
                role_id,
                menu_id,
                can_view,
                can_add,
                can_edit,
                can_delete,
                created_by,
                updated_by,
                updated_at
            )
            VALUES (
                :roleId,
                :menuId,
                TRUE,
                FALSE,
                FALSE,
                FALSE,
                :userId,
                :userId,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (role_id, menu_id) DO UPDATE
            SET
                can_view = TRUE,
                can_add = EXCLUDED.can_add,
                can_edit = EXCLUDED.can_edit,
                can_delete = EXCLUDED.can_delete,
                updated_by = EXCLUDED.updated_by,
                updated_at = CURRENT_TIMESTAMP
            """,
            batch
        );
    }
}
