package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.ContractDashboardBranchDto;
import dugar_lms_api.modules.contracts.dto.ContractDashboardDisbursementDto;
import dugar_lms_api.modules.contracts.dto.ContractDashboardTotalsDto;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ContractDashboardRepository {

    private static final String ACTIVE_FILTER = """
        WHERE c.is_active = TRUE
          AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
          AND EXISTS (
              SELECT 1
              FROM contract_repayment_structures repayment_exists
              WHERE repayment_exists.contract_id = c.contract_id
          )
        """;

    private static final String ACCESS_JOIN = """
        LEFT JOIN users created_user
          ON c.created_by = created_user.user_id::text
          OR LOWER(TRIM(COALESCE(c.created_by, ''))) = LOWER(TRIM(created_user.username))
        LEFT JOIN users updated_user
          ON c.updated_by = updated_user.user_id::text
          OR LOWER(TRIM(COALESCE(c.updated_by, ''))) = LOWER(TRIM(updated_user.username))
        """;

    private static final String ACCESS_FILTER = """
          AND (
              :restricted = FALSE
              OR (
                  LOWER(TRIM(COALESCE(created_user.user_group, ''))) = 'user'
                  AND (
                      NULLIF(TRIM(COALESCE(c.updated_by, '')), '') IS NULL
                      OR LOWER(TRIM(COALESCE(updated_user.user_group, ''))) = 'user'
                  )
              )
          )
        """;

    private static final String BRANCH_DATA_SQL = """
        SELECT
            COALESCE(NULLIF(TRIM(c.area_code), ''), 'Unassigned') AS branch_name,
            COUNT(*)::BIGINT AS loans,
            COALESCE(SUM(c.total_contract_value), 0) / 100000 AS aum_lakhs
        FROM contracts c
        """ + ACCESS_JOIN + """
        """ + ACTIVE_FILTER + """
        """ + ACCESS_FILTER + """
        GROUP BY COALESCE(NULLIF(TRIM(c.area_code), ''), 'Unassigned')
        ORDER BY loans DESC, branch_name
        """;

    private static final String TOTALS_SQL = """
        SELECT
            COUNT(*)::BIGINT AS total_active_contracts,
            COALESCE(SUM(c.total_contract_value), 0) / 100000 AS total_aum_lakhs
        FROM contracts c
        """ + ACCESS_JOIN + """
        """ + ACTIVE_FILTER + """
        """ + ACCESS_FILTER + """
        """;

    private static final String AVAILABLE_YEARS_SQL = """
        SELECT DISTINCT EXTRACT(YEAR FROM c.contract_date)::INTEGER AS account_year
        FROM contracts c
        """ + ACCESS_JOIN + """
        """ + ACTIVE_FILTER + """
          """ + ACCESS_FILTER + """
          AND c.contract_date IS NOT NULL
        ORDER BY account_year DESC
        """;

    private static final String DISBURSEMENT_DATA_SQL = """
        WITH latest_contract AS (
            SELECT MAX(c.contract_date) AS latest_date
            FROM contracts c
            """ + ACCESS_JOIN + """
            """ + ACTIVE_FILTER + """
              """ + ACCESS_FILTER + """
              AND c.contract_date IS NOT NULL
              AND EXTRACT(YEAR FROM c.contract_date)::INTEGER = :accountYear
        )
        SELECT
            CASE
                WHEN :period = 'ALL' THEN EXTRACT(YEAR FROM c.contract_date)::INTEGER::TEXT
                WHEN :period = 'YTD' THEN TO_CHAR(date_trunc('month', c.contract_date), 'Mon YYYY')
                ELSE TO_CHAR(c.contract_date, 'DD Mon')
            END AS day_label,
            CASE
                WHEN :period = 'ALL' THEN date_trunc('year', c.contract_date)
                WHEN :period = 'YTD' THEN date_trunc('month', c.contract_date)
                ELSE c.contract_date
            END AS sort_key,
            COALESCE(SUM(c.total_contract_value), 0) / 100000 AS disbursement_lakhs
        FROM contracts c
        CROSS JOIN latest_contract lc
        """ + ACCESS_JOIN + """
        """ + ACTIVE_FILTER + """
          """ + ACCESS_FILTER + """
          AND (
              :period = 'ALL'
              OR (:period = 'YTD'
                  AND EXTRACT(YEAR FROM c.contract_date)::INTEGER = :accountYear)
              OR (:period = 'MTD'
                  AND lc.latest_date IS NOT NULL
                  AND c.contract_date >= date_trunc('month', lc.latest_date)
                  AND c.contract_date < date_trunc('month', lc.latest_date) + INTERVAL '1 month')
          )
        GROUP BY day_label, sort_key
        ORDER BY sort_key
        """;

    private static final RowMapper<ContractDashboardBranchDto> BRANCH_ROW_MAPPER = (rs, rowNum) -> new ContractDashboardBranchDto(
        rs.getString("branch_name"),
        rs.getLong("loans"),
        rs.getBigDecimal("aum_lakhs")
    );

    private static final RowMapper<ContractDashboardDisbursementDto> DISBURSEMENT_ROW_MAPPER = (rs, rowNum) -> new ContractDashboardDisbursementDto(
        rs.getString("day_label"),
        rs.getBigDecimal("disbursement_lakhs")
    );

    private static final RowMapper<ContractDashboardTotalsDto> TOTALS_ROW_MAPPER = (rs, rowNum) -> new ContractDashboardTotalsDto(
        rs.getLong("total_active_contracts"),
        rs.getBigDecimal("total_aum_lakhs")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ContractDashboardRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<ContractDashboardBranchDto> branchData() {
        return branchData(new ReportAccessScope(false));
    }

    public List<ContractDashboardBranchDto> branchData(ReportAccessScope accessScope) {
        return namedParameterJdbcTemplate.query(BRANCH_DATA_SQL, accessParams(accessScope), BRANCH_ROW_MAPPER);
    }

    public ContractDashboardTotalsDto totals() {
        return totals(new ReportAccessScope(false));
    }

    public ContractDashboardTotalsDto totals(ReportAccessScope accessScope) {
        return namedParameterJdbcTemplate.queryForObject(TOTALS_SQL, accessParams(accessScope), TOTALS_ROW_MAPPER);
    }

    public List<Integer> availableYears() {
        return availableYears(new ReportAccessScope(false));
    }

    public List<Integer> availableYears(ReportAccessScope accessScope) {
        return namedParameterJdbcTemplate.queryForList(AVAILABLE_YEARS_SQL, accessParams(accessScope), Integer.class);
    }

    public List<ContractDashboardDisbursementDto> disbursementData(String period, Integer accountYear) {
        return disbursementData(period, accountYear, new ReportAccessScope(false));
    }

    public List<ContractDashboardDisbursementDto> disbursementData(String period, Integer accountYear, ReportAccessScope accessScope) {
        return namedParameterJdbcTemplate.query(
            DISBURSEMENT_DATA_SQL,
            accessParams(accessScope)
                .addValue("period", normalizePeriod(period))
                .addValue("accountYear", accountYear),
            DISBURSEMENT_ROW_MAPPER
        );
    }

    private MapSqlParameterSource accessParams(ReportAccessScope accessScope) {
        return new MapSqlParameterSource()
            .addValue("restricted", accessScope != null && accessScope.restrictedToUserGroup());
    }

    private String normalizePeriod(String period) {
        if (period == null) {
            return "YTD";
        }

        String normalized = period.trim().toUpperCase();
        return switch (normalized) {
            case "MTD", "ALL" -> normalized;
            default -> "YTD";
        };
    }
}
