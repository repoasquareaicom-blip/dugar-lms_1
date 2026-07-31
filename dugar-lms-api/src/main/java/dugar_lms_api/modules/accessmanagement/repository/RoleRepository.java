package dugar_lms_api.modules.accessmanagement.repository;

import dugar_lms_api.modules.accessmanagement.mapper.RoleRowMapper;
import dugar_lms_api.modules.accessmanagement.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    private static final String EXISTS_BY_CODE_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM roles
            WHERE LOWER(role_code) = LOWER(:roleCode)
        )
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

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

    public boolean existsByCode(String roleCode) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roleCode", roleCode);

        Boolean exists = namedParameterJdbcTemplate.queryForObject(EXISTS_BY_CODE_SQL, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }
}
