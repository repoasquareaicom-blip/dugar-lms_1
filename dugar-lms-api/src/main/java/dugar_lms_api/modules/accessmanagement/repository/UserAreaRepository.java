package dugar_lms_api.modules.accessmanagement.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserAreaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserAreaRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findAreaCodesByUserId(Long userId) {
        return jdbcTemplate.query(
            """
            SELECT area_code
            FROM user_areas
            WHERE user_id = :userId
            ORDER BY area_code
            """,
            new MapSqlParameterSource("userId", userId),
            (rs, rowNum) -> rs.getString("area_code")
        );
    }

    public void replaceUserAreas(Long userId, List<String> areaCodes) {
        deleteByUserId(userId);
        if (areaCodes == null || areaCodes.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] batch = areaCodes.stream()
            .map(areaCode -> new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("areaCode", areaCode))
            .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO user_areas (user_id, area_code)
            VALUES (:userId, :areaCode)
            ON CONFLICT (user_id, area_code) DO NOTHING
            """,
            batch
        );
    }

    public void deleteByUserId(Long userId) {
        jdbcTemplate.update(
            "DELETE FROM user_areas WHERE user_id = :userId",
            new MapSqlParameterSource("userId", userId)
        );
    }
}
