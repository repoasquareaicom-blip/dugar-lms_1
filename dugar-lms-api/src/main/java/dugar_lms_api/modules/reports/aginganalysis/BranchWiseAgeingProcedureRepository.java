package dugar_lms_api.modules.reports.aginganalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class BranchWiseAgeingProcedureRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(BranchWiseAgeingProcedureRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public BranchWiseAgeingProcedureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AgingAnalysisBranchRowDto> getBranchWiseRows(LocalDate asOnDate, String areaCode) {
        long startedAt = System.nanoTime();
        List<AgingAnalysisBranchRowDto> rows = jdbcTemplate.execute((ConnectionCallback<List<AgingAnalysisBranchRowDto>>) (connection) -> {
            String normalizedArea = clean(areaCode);
            try (var call = connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?)")) {
                call.setObject(1, asOnDate);
                call.setString(2, normalizedArea);
                call.execute();
            }

            String sql = """
                SELECT
                    area_code,
                    COUNT(*) AS no_of_accounts,
                    COALESCE(SUM(principal_outstanding), 0) AS aum,
                    COALESCE(SUM(current_due), 0) AS current_amount,
                    COALESCE(SUM(overdue_amount) FILTER (WHERE ageing_bucket = '1-30'), 0) AS days_1_30,
                    COALESCE(SUM(overdue_amount) FILTER (WHERE ageing_bucket = '31-60'), 0) AS days_31_60,
                    COALESCE(SUM(overdue_amount) FILTER (WHERE ageing_bucket = '61-90'), 0) AS days_61_90,
                    COALESCE(SUM(overdue_amount) FILTER (WHERE ageing_bucket = '91-120'), 0) AS days_91_120,
                    COALESCE(SUM(overdue_amount) FILTER (WHERE ageing_bucket = '121-150'), 0) AS days_121_150,
                    COALESCE(SUM(overdue_amount) FILTER (WHERE ageing_bucket = '151-180'), 0) AS days_151_180,
                    COALESCE(SUM(overdue_amount) FILTER (WHERE ageing_bucket = 'ABOVE 180'), 0) AS days_above_180,
                    COALESCE(SUM(current_due), 0) + COALESCE(SUM(overdue_amount), 0) AS total_outstanding,
                    COALESCE(SUM(interest_outstanding), 0) AS interest_outstanding
                FROM tmp_contract_report
                WHERE (? IS NULL OR UPPER(TRIM(COALESCE(area_code, ''))) = ?)
                  AND COALESCE(total_outstanding, 0) > 0
                GROUP BY area_code
                ORDER BY area_code
                """;
            try (var select = connection.prepareStatement(sql)) {
                select.setString(1, normalizedArea);
                select.setString(2, normalizedArea);
                try (ResultSet rs = select.executeQuery()) {
                    List<AgingAnalysisBranchRowDto> result = new ArrayList<>();
                    Set<String> columns = columns(rs);
                    while (rs.next()) {
                        result.add(new AgingAnalysisBranchRowDto(
                            rs.getString("area_code"),
                            rs.getLong("no_of_accounts"),
                            money(rs.getBigDecimal("aum")),
                            money(rs.getBigDecimal("current_amount")),
                            money(rs.getBigDecimal("days_1_30")),
                            money(rs.getBigDecimal("days_31_60")),
                            money(rs.getBigDecimal("days_61_90")),
                            money(rs.getBigDecimal("days_91_120")),
                            money(rs.getBigDecimal("days_121_150")),
                            money(rs.getBigDecimal("days_151_180")),
                            money(rs.getBigDecimal("days_above_180")),
                            money(rs.getBigDecimal("total_outstanding")),
                            money(optionalMoney(rs, columns, "outstanding_interest_amount", "interest_outstanding", "outstanding_interest"))
                        ));
                    }
                    return result;
                }
            }
        });
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        LOGGER.info("Branch Wise Ageing procedure completed in {} ms for date {} and area {}", elapsedMs, asOnDate, areaCode);
        return rows == null ? List.of() : rows;
    }

    public List<ProcedureContractReportRow> getContractReportRows(LocalDate asOnDate, String areaCode) {
        long startedAt = System.nanoTime();
        List<ProcedureContractReportRow> rows = jdbcTemplate.execute((ConnectionCallback<List<ProcedureContractReportRow>>) (connection) -> {
            String normalizedArea = clean(areaCode);
            try (var call = connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?)")) {
                call.setObject(1, asOnDate);
                call.setString(2, normalizedArea);
                call.execute();
            }

            String sql = """
                SELECT
                    r.contract_id,
                    r.contract_number,
                    r.contract_type,
                    r.area_code,
                    r.borrower_code,
                    COALESCE(NULLIF(TRIM(pm.full_name), ''), NULLIF(TRIM(r.borrower_code), '')) AS borrower_name,
                    r.guarantor_code,
                    COALESCE(NULLIF(TRIM(gm.full_name), ''), NULLIF(TRIM(r.guarantor_code), '')) AS guarantor_name,
                    r.contract_date,
                    r.first_emi_date,
                    r.vehicle_make,
                    r.registration_number,
                    r.owner_serial_no,
                    r.category,
                    COALESCE(r.total_contract_value, 0) AS total_contract_value,
                    COALESCE(r.original_principal, 0) AS loan_amount,
                    COALESCE(r.total_received, 0) AS total_received,
                    COALESCE(c.flat_interest_rate, 0) AS flat_interest_rate,
                    COALESCE(r.principal_outstanding, 0) AS principal_outstanding,
                    COALESCE(r.interest_outstanding, 0) AS interest_outstanding,
                    COALESCE(r.total_outstanding, 0) AS total_outstanding,
                    COALESCE(r.overdue_emi_count, 0) AS overdue_emi_count,
                    COALESCE(r.overdue_amount, 0) AS overdue_amount,
                    r.overdue_from_date,
                    r.overdue_end_date,
                    COALESCE(r.current_due, 0) AS current_due,
                    r.current_due_date,
                    r.ageing_bucket
                FROM tmp_contract_report r
                LEFT JOIN contracts c
                  ON c.contract_id = r.contract_id
                LEFT JOIN party_masters pm
                  ON UPPER(TRIM(pm.party_code)) = UPPER(TRIM(r.borrower_code))
                 AND pm.is_active = TRUE
                LEFT JOIN party_masters gm
                  ON UPPER(TRIM(gm.party_code)) = UPPER(TRIM(r.guarantor_code))
                 AND gm.is_active = TRUE
                WHERE (? IS NULL OR UPPER(TRIM(COALESCE(r.area_code, ''))) = ?)
                ORDER BY r.area_code, r.contract_number
                """;
            try (var select = connection.prepareStatement(sql)) {
                select.setString(1, normalizedArea);
                select.setString(2, normalizedArea);
                try (ResultSet rs = select.executeQuery()) {
                    List<ProcedureContractReportRow> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(new ProcedureContractReportRow(
                            rs.getLong("contract_id"),
                            rs.getString("contract_number"),
                            rs.getString("contract_type"),
                            rs.getString("area_code"),
                            rs.getString("borrower_code"),
                            rs.getString("borrower_name"),
                            rs.getString("guarantor_code"),
                            rs.getString("guarantor_name"),
                            rs.getObject("contract_date", LocalDate.class),
                            rs.getObject("first_emi_date", LocalDate.class),
                            rs.getString("vehicle_make"),
                            rs.getString("registration_number"),
                            rs.getString("owner_serial_no"),
                            rs.getString("category"),
                            money(rs.getBigDecimal("total_contract_value")),
                            money(rs.getBigDecimal("loan_amount")),
                            money(rs.getBigDecimal("total_received")),
                            money(rs.getBigDecimal("flat_interest_rate")),
                            money(rs.getBigDecimal("principal_outstanding")),
                            money(rs.getBigDecimal("interest_outstanding")),
                            money(rs.getBigDecimal("total_outstanding")),
                            rs.getInt("overdue_emi_count"),
                            money(rs.getBigDecimal("overdue_amount")),
                            rs.getObject("overdue_from_date", LocalDate.class),
                            rs.getObject("overdue_end_date", LocalDate.class),
                            money(rs.getBigDecimal("current_due")),
                            rs.getObject("current_due_date", LocalDate.class),
                            rs.getString("ageing_bucket")
                        ));
                    }
                    return result;
                }
            }
        });
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        LOGGER.info("Contract Ageing procedure report completed in {} ms for date {} and area {}", elapsedMs, asOnDate, areaCode);
        return rows == null ? List.of() : rows;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Set<String> columns(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        Set<String> columns = new HashSet<>();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            columns.add(metaData.getColumnLabel(index).toLowerCase());
        }
        return columns;
    }

    private BigDecimal optionalMoney(ResultSet rs, Set<String> columns, String... names) throws SQLException {
        for (String name : names) {
            if (columns.contains(name.toLowerCase())) {
                return rs.getBigDecimal(name);
            }
        }
        return BigDecimal.ZERO;
    }

    public record ProcedureContractReportRow(
        Long contractId,
        String contractNumber,
        String contractType,
        String areaCode,
        String borrowerCode,
        String borrowerName,
        String guarantorCode,
        String guarantorName,
        LocalDate contractDate,
        LocalDate firstEmiDate,
        String vehicleMake,
        String registrationNumber,
        String ownerSerialNo,
        String category,
        BigDecimal totalContractValue,
        BigDecimal loanAmount,
        BigDecimal totalReceived,
        BigDecimal flatInterestRate,
        BigDecimal principalOutstanding,
        BigDecimal interestOutstanding,
        BigDecimal totalOutstanding,
        Integer overdueEmiCount,
        BigDecimal overdueAmount,
        LocalDate overdueFromDate,
        LocalDate overdueEndDate,
        BigDecimal currentDue,
        LocalDate currentDueDate,
        String ageingBucket
    ) {
    }
}
