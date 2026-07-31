package dugar_lms_api.modules.accessmanagement.mapper;

import dugar_lms_api.modules.accessmanagement.model.Menu;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class MenuRowMapper implements RowMapper<Menu> {

    @Override
    public Menu mapRow(ResultSet rs, int rowNum) throws SQLException {
        Menu menu = new Menu();

        menu.setMenuId(rs.getObject("menu_id", Long.class));
        menu.setMenuName(rs.getString("menu_name"));
        menu.setParentId(rs.getObject("parent_id", Long.class));
        menu.setUrlPath(rs.getString("url_path"));
        menu.setIcon(rs.getString("icon"));
        menu.setDisplayOrder(rs.getObject("display_order", Integer.class));
        menu.setMenuCode(rs.getString("menu_code"));
        menu.setMenuType(rs.getString("menu_type"));
        menu.setIsActive(rs.getObject("is_active", Boolean.class));
        menu.setIsVisible(rs.getObject("is_visible", Boolean.class));
        menu.setCreatedBy(rs.getObject("created_by", Long.class));
        menu.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        menu.setUpdatedBy(rs.getObject("updated_by", Long.class));
        menu.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));

        return menu;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
