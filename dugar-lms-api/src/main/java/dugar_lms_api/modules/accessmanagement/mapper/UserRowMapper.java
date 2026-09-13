package dugar_lms_api.modules.accessmanagement.mapper;

import dugar_lms_api.modules.accessmanagement.model.User;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class UserRowMapper implements RowMapper<User> {

    @Override
    public User mapRow(ResultSet rs, int rowNum) throws SQLException {
        User user = new User();

        user.setUserId(rs.getObject("user_id", Long.class));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setEmailId(rs.getString("email_id"));
        user.setRoleId(rs.getObject("role_id", Long.class));
        user.setUserGroup(rs.getString("user_group"));
        user.setUserType(rs.getString("user_type"));
        user.setIsActive(rs.getObject("is_active", Boolean.class));
        user.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        user.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        user.setLastLoginAt(toLocalDateTime(rs.getTimestamp("last_login_at")));

        return user;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
