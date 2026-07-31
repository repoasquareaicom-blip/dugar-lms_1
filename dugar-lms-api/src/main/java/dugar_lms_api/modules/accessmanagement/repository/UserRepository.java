package dugar_lms_api.modules.accessmanagement.repository;

import dugar_lms_api.modules.accessmanagement.mapper.UserRowMapper;
import dugar_lms_api.modules.accessmanagement.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private static final String BASE_USER_COLUMNS = """
        SELECT
            user_id,
            username,
            password_hash,
            full_name,
            email_id,
            role_id,
            is_active,
            created_at,
            updated_at,
            last_login_at
        FROM users
        """;

    private static final String FIND_ACTIVE_BY_USERNAME_SQL = BASE_USER_COLUMNS + """
        WHERE LOWER(username) = LOWER(:username)
          AND is_active = TRUE
        """;

    private static final String FIND_BY_ID_SQL = BASE_USER_COLUMNS + """
        WHERE user_id = :userId
        """;

    private static final String EXISTS_BY_USERNAME_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM users
            WHERE LOWER(username) = LOWER(:username)
        )
        """;

    private static final String UPDATE_LAST_LOGIN_SQL = """
        UPDATE users
        SET last_login_at = :lastLoginAt,
            updated_at = CURRENT_TIMESTAMP
        WHERE user_id = :userId
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public Optional<User> findActiveByUsername(String username) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("username", username);

        List<User> users = namedParameterJdbcTemplate.query(FIND_ACTIVE_BY_USERNAME_SQL, params, new UserRowMapper());
        return users.stream().findFirst();
    }

    public Optional<User> findById(Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId);

        List<User> users = namedParameterJdbcTemplate.query(FIND_BY_ID_SQL, params, new UserRowMapper());
        return users.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("username", username);

        Boolean exists = namedParameterJdbcTemplate.queryForObject(EXISTS_BY_USERNAME_SQL, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public int updateLastLogin(Long userId, LocalDateTime lastLoginAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("lastLoginAt", lastLoginAt);

        return namedParameterJdbcTemplate.update(UPDATE_LAST_LOGIN_SQL, params);
    }
}
