package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContractAccessRepository {

    private static final String ACCESS_FILTER = """
        (
            :fullAccess = TRUE
            OR (
                EXISTS (
                    SELECT 1
                    FROM users access_user
                    WHERE access_user.user_id::text = TRIM(COALESCE(c.updated_by, ''))
                      AND LOWER(TRIM(COALESCE(access_user.user_group, ''))) = 'user'
                )
                AND (
                    EXISTS (SELECT 1 FROM users scope_user WHERE scope_user.user_id = :scopeUserId AND UPPER(TRIM(COALESCE(scope_user.user_type, 'USER'))) = 'USER')
                    OR EXISTS (
                        SELECT 1
                        FROM user_areas scope_area
                        JOIN users scope_user ON scope_user.user_id = scope_area.user_id
                        WHERE scope_area.user_id = :scopeUserId
                          AND UPPER(TRIM(COALESCE(scope_user.user_type, ''))) IN ('BRANCH', 'STATE')
                          AND UPPER(TRIM(scope_area.area_code)) = UPPER(TRIM(COALESCE(c.area_code, '')))
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_contracts scope_contract
                        JOIN users scope_user ON scope_user.user_id = scope_contract.user_id
                        WHERE scope_contract.user_id = :scopeUserId
                          AND UPPER(TRIM(COALESCE(scope_user.user_type, ''))) = 'CUSTOMER'
                          AND UPPER(TRIM(scope_contract.contract_number)) = UPPER(TRIM(COALESCE(c.contract_number, '')))
                    )
                )
            )
        )
        """;

    private static final String ACCESSIBLE_CONTRACT_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM contracts c
            WHERE c.contract_id = :contractId
              AND """ + ACCESS_FILTER + """
        )
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ContractAccessRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean canAccess(Long contractId, ReportAccessScope accessScope) {
        if (contractId == null) {
            return false;
        }
        Boolean allowed = jdbcTemplate.queryForObject(
            ACCESSIBLE_CONTRACT_SQL,
            new MapSqlParameterSource()
                .addValue("contractId", contractId)
                .addValue("fullAccess", accessScope == null || accessScope.fullAccess())
                .addValue("scopeUserId", accessScope == null ? null : accessScope.userId()),
            Boolean.class
        );
        return Boolean.TRUE.equals(allowed);
    }

    public void requireAccess(Long contractId, ReportAccessScope accessScope) {
        if (!canAccess(contractId, accessScope)) {
            throw new IllegalArgumentException("Contract not found or not accessible.");
        }
    }
}
