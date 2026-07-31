package dugar_lms_api.modules.accessmanagement.repository;

import dugar_lms_api.modules.accessmanagement.mapper.MenuRowMapper;
import dugar_lms_api.modules.accessmanagement.model.Menu;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MenuRepository {

    private static final String BASE_MENU_COLUMNS = """
        SELECT
            menu_id,
            menu_name,
            parent_id,
            url_path,
            icon,
            display_order,
            menu_code,
            menu_type,
            is_active,
            is_visible,
            created_by,
            created_at,
            updated_by,
            updated_at
        FROM menus
        """;

    private static final String FIND_BY_ID_SQL = BASE_MENU_COLUMNS + """
        WHERE menu_id = :menuId
        """;

    private static final String FIND_ACTIVE_BY_CODE_SQL = BASE_MENU_COLUMNS + """
        WHERE LOWER(menu_code) = LOWER(:menuCode)
          AND is_active = TRUE
        """;

    private static final String FIND_ALL_ACTIVE_VISIBLE_SQL = BASE_MENU_COLUMNS + """
        WHERE is_active = TRUE
          AND is_visible = TRUE
        ORDER BY display_order, menu_name
        """;

    private static final String FIND_ACTIVE_VISIBLE_BY_PARENT_ID_SQL = BASE_MENU_COLUMNS + """
        WHERE parent_id = :parentId
          AND is_active = TRUE
          AND is_visible = TRUE
        ORDER BY display_order, menu_name
        """;

    private static final String FIND_ACTIVE_VISIBLE_ROOT_MENUS_SQL = BASE_MENU_COLUMNS + """
        WHERE parent_id IS NULL
          AND is_active = TRUE
          AND is_visible = TRUE
        ORDER BY display_order, menu_name
        """;

        private static final String FIND_ACTIVE_VISIBLE_BY_IDS_SQL = BASE_MENU_COLUMNS + """
                WHERE menu_id IN (:menuIds)
                    AND is_active = TRUE
                    AND is_visible = TRUE
                ORDER BY display_order, menu_name
                """;

    private static final String EXISTS_BY_CODE_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM menus
            WHERE LOWER(menu_code) = LOWER(:menuCode)
        )
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public Optional<Menu> findById(Long menuId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("menuId", menuId);

        List<Menu> menus = namedParameterJdbcTemplate.query(FIND_BY_ID_SQL, params, new MenuRowMapper());
        return menus.stream().findFirst();
    }

    public Optional<Menu> findActiveByCode(String menuCode) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("menuCode", menuCode);

        List<Menu> menus = namedParameterJdbcTemplate.query(FIND_ACTIVE_BY_CODE_SQL, params, new MenuRowMapper());
        return menus.stream().findFirst();
    }

    public List<Menu> findAllActiveVisible() {
        return namedParameterJdbcTemplate.query(FIND_ALL_ACTIVE_VISIBLE_SQL, new MenuRowMapper());
    }

    public List<Menu> findActiveVisibleByParentId(Long parentId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("parentId", parentId);

        return namedParameterJdbcTemplate.query(FIND_ACTIVE_VISIBLE_BY_PARENT_ID_SQL, params, new MenuRowMapper());
    }

    public List<Menu> findActiveVisibleRootMenus() {
        return namedParameterJdbcTemplate.query(FIND_ACTIVE_VISIBLE_ROOT_MENUS_SQL, new MenuRowMapper());
    }

    public List<Menu> findActiveVisibleByIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptyList();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("menuIds", menuIds);

        return namedParameterJdbcTemplate.query(FIND_ACTIVE_VISIBLE_BY_IDS_SQL, params, new MenuRowMapper());
    }

    public boolean existsByCode(String menuCode) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("menuCode", menuCode);

        Boolean exists = namedParameterJdbcTemplate.queryForObject(EXISTS_BY_CODE_SQL, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }
}
