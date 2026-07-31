package dugar_lms_api.modules.accessmanagement.mapper;

import dugar_lms_api.modules.accessmanagement.model.Role;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class RoleRowMapper implements RowMapper<Role> {

    @Override
    public Role mapRow(ResultSet rs, int rowNum) throws SQLException {
        Role role = new Role();

        role.setRoleId(rs.getObject("role_id", Long.class));
        role.setRoleName(rs.getString("role_name"));
        role.setRoleCode(rs.getString("role_code"));
        role.setIsActive(rs.getObject("is_active", Boolean.class));
        role.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        role.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));

        return role;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
