package dugar_lms_api.modules.accessmanagement.mapper;

import dugar_lms_api.modules.accessmanagement.model.RolePermission;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class RolePermissionRowMapper implements RowMapper<RolePermission> {

    @Override
    public RolePermission mapRow(ResultSet rs, int rowNum) throws SQLException {
        RolePermission rolePermission = new RolePermission();

        rolePermission.setPermissionId(rs.getObject("permission_id", Long.class));
        rolePermission.setRoleId(rs.getObject("role_id", Long.class));
        rolePermission.setMenuId(rs.getObject("menu_id", Long.class));
        rolePermission.setCanView(rs.getObject("can_view", Boolean.class));
        rolePermission.setCanAdd(rs.getObject("can_add", Boolean.class));
        rolePermission.setCanEdit(rs.getObject("can_edit", Boolean.class));
        rolePermission.setCanDelete(rs.getObject("can_delete", Boolean.class));
        rolePermission.setCreatedBy(rs.getObject("created_by", Long.class));
        rolePermission.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        rolePermission.setUpdatedBy(rs.getObject("updated_by", Long.class));
        rolePermission.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));

        return rolePermission;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
