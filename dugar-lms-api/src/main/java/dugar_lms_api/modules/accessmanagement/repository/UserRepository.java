package dugar_lms_api.modules.accessmanagement.repository;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accessmanagement.dto.UserManagementRequest;
import dugar_lms_api.modules.accessmanagement.mapper.UserRowMapper;
import dugar_lms_api.modules.accessmanagement.model.User;
import dugar_lms_api.modules.accessmanagement.service.UserManagementCriteria;
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
            COALESCE(NULLIF(TRIM(user_group), ''), 'admin') AS user_group,
            COALESCE(NULLIF(TRIM(user_type), ''), 'USER') AS user_type,
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

    public PageResponse<User> find(UserManagementCriteria criteria) {
        int page = criteria.page() == null || criteria.page() < 0 ? 0 : criteria.page();
        int size = criteria.size() == null || criteria.size() <= 0 ? 25 : Math.min(criteria.size(), 200);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", size)
            .addValue("offset", page * size);

        String where = where(criteria, params);
        long total = namedParameterJdbcTemplate.queryForObject("SELECT COUNT(*) FROM users " + where, params, Long.class);
        List<User> content = namedParameterJdbcTemplate.query(
            BASE_USER_COLUMNS + where + " ORDER BY " + sortColumn(criteria.sortColumn()) + " " + sortDirection(criteria.sortDirection()) + " LIMIT :limit OFFSET :offset",
            params,
            new UserRowMapper()
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

    public boolean existsByUsername(String username, Long excludeUserId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("username", clean(username));

        String sql = excludeUserId == null
            ? """
            SELECT EXISTS (
                SELECT 1
                FROM users
                WHERE LOWER(username) = LOWER(:username)
            )
            """
            : """
            SELECT EXISTS (
                SELECT 1
                FROM users
                WHERE LOWER(username) = LOWER(:username)
                  AND user_id <> :excludeUserId
            )
            """;

        if (excludeUserId != null) {
            params.addValue("excludeUserId", excludeUserId);
        }

        Boolean exists = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public User create(UserManagementRequest request, String passwordHash, Long primaryRoleId) {
        return namedParameterJdbcTemplate.queryForObject(
            """
            INSERT INTO users (
                username,
                password_hash,
                full_name,
                role_id,
                user_group,
                user_type,
                is_active
            )
            VALUES (
                :username,
                :passwordHash,
                :fullName,
                :roleId,
                :userGroup,
                :userType,
                COALESCE(:isActive, TRUE)
            )
            RETURNING
                user_id,
                username,
                password_hash,
                full_name,
                email_id,
                role_id,
                COALESCE(NULLIF(TRIM(user_group), ''), 'admin') AS user_group,
                COALESCE(NULLIF(TRIM(user_type), ''), 'USER') AS user_type,
                is_active,
                created_at,
                updated_at,
                last_login_at
            """,
            params(request)
                .addValue("passwordHash", passwordHash)
                .addValue("roleId", primaryRoleId),
            new UserRowMapper()
        );
    }

    public Long nextUserId() {
        return namedParameterJdbcTemplate.queryForObject(
            "SELECT nextval(pg_get_serial_sequence('users', 'user_id'))",
            new MapSqlParameterSource(),
            Long.class
        );
    }

    public User createWithId(Long userId, UserManagementRequest request, String passwordHash, Long primaryRoleId) {
        return namedParameterJdbcTemplate.queryForObject(
            """
            INSERT INTO users (
                user_id,
                username,
                password_hash,
                full_name,
                role_id,
                user_group,
                user_type,
                is_active
            )
            VALUES (
                :userId,
                :username,
                :passwordHash,
                :fullName,
                :roleId,
                :userGroup,
                :userType,
                COALESCE(:isActive, TRUE)
            )
            RETURNING
                user_id,
                username,
                password_hash,
                full_name,
                email_id,
                role_id,
                COALESCE(NULLIF(TRIM(user_group), ''), 'admin') AS user_group,
                COALESCE(NULLIF(TRIM(user_type), ''), 'USER') AS user_type,
                is_active,
                created_at,
                updated_at,
                last_login_at
            """,
            params(request)
                .addValue("userId", userId)
                .addValue("passwordHash", passwordHash)
                .addValue("roleId", primaryRoleId),
            new UserRowMapper()
        );
    }

    public User update(Long userId, UserManagementRequest request, String passwordHash, Long primaryRoleId) {
        MapSqlParameterSource params = params(request)
            .addValue("userId", userId)
            .addValue("roleId", primaryRoleId)
            .addValue("passwordHash", passwordHash);

        return namedParameterJdbcTemplate.queryForObject(
            """
            UPDATE users
            SET
                username = :username,
                password_hash = COALESCE(:passwordHash, password_hash),
                full_name = :fullName,
                role_id = :roleId,
                user_group = :userGroup,
                user_type = :userType,
                is_active = COALESCE(:isActive, is_active),
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
            RETURNING
                user_id,
                username,
                password_hash,
                full_name,
                email_id,
                role_id,
                COALESCE(NULLIF(TRIM(user_group), ''), 'admin') AS user_group,
                COALESCE(NULLIF(TRIM(user_type), ''), 'USER') AS user_type,
                is_active,
                created_at,
                updated_at,
                last_login_at
            """,
            params,
            new UserRowMapper()
        );
    }

    public int updateLastLogin(Long userId, LocalDateTime lastLoginAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("lastLoginAt", lastLoginAt);

        return namedParameterJdbcTemplate.update(UPDATE_LAST_LOGIN_SQL, params);
    }

    private String where(UserManagementCriteria criteria, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            where.append("""
                 AND (
                    username ILIKE :keyword
                    OR full_name ILIKE :keyword
                    OR user_group ILIKE :keyword
                    OR user_type ILIKE :keyword
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

    private MapSqlParameterSource params(UserManagementRequest request) {
        return new MapSqlParameterSource()
            .addValue("username", clean(request.username()))
            .addValue("fullName", clean(request.fullName()))
            .addValue("userGroup", "user")
            .addValue("userType", cleanUserType(request.userType()))
            .addValue("isActive", request.isActive());
    }

    private String sortColumn(String sortColumn) {
        if (sortColumn == null) {
            return "username";
        }
        return switch (sortColumn) {
            case "fullName" -> "full_name";
            case "userGroup" -> "user_group";
            case "userType" -> "user_type";
            case "isActive" -> "is_active";
            case "updatedAt" -> "updated_at";
            default -> "username";
        };
    }

    private String sortDirection(String sortDirection) {
        return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String cleanUserType(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "USER" : cleaned.toUpperCase();
    }
}
