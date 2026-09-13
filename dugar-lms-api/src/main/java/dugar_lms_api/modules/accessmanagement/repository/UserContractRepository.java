package dugar_lms_api.modules.accessmanagement.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserContractRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserContractRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findContractNumberByUserId(Long userId) {
        return jdbcTemplate.query(
            """
            SELECT contract_number
            FROM user_contracts
            WHERE user_id = :userId
            ORDER BY user_contract_id
            LIMIT 1
            """,
            new MapSqlParameterSource("userId", userId),
            (rs, rowNum) -> rs.getString("contract_number")
        ).stream().findFirst();
    }

    public void replaceUserContract(Long userId, String contractNumber) {
        deleteByUserId(userId);
        jdbcTemplate.update(
            """
            INSERT INTO user_contracts (user_id, contract_number)
            VALUES (:userId, :contractNumber)
            ON CONFLICT (user_id, contract_number) DO NOTHING
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("contractNumber", contractNumber)
        );
    }

    public void deleteByUserId(Long userId) {
        jdbcTemplate.update(
            "DELETE FROM user_contracts WHERE user_id = :userId",
            new MapSqlParameterSource("userId", userId)
        );
    }
}
