package dugar_lms_api.modules.reports.aginganalysis;

import dugar_lms_api.modules.reports.ReportAccessScope;
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
        return getBranchWiseRows(asOnDate, areaCode, new ReportAccessScope(false));
    }

    public List<AgingAnalysisBranchRowDto> getBranchWiseRows(LocalDate asOnDate, String areaCode, ReportAccessScope accessScope) {
        long startedAt = System.nanoTime();
        List<AgingAnalysisBranchRowDto> rows = jdbcTemplate.execute((ConnectionCallback<List<AgingAnalysisBranchRowDto>>) (connection) -> {
            String normalizedArea = clean(areaCode);
            try (var call = connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?, ?)")) {
                call.setObject(1, asOnDate);
                call.setString(2, normalizedArea);
                call.setString(3, userGroup(accessScope));
                call.execute();
            }

           String sql = """
            SELECT
                bwa.area_code,
                NULLIF(TRIM(am.area_name), '') AS area_name,
                bwa.no_of_accounts,
                bwa.aum,
                bwa.current_amount,
                bwa.days_1_30,
                bwa.days_31_60,
                bwa.days_61_90,
                bwa.days_91_120,
                bwa.days_121_150,
                bwa.days_151_180,
                bwa.days_above_180,
                bwa.total_outstanding,
                COALESCE(SUM(tcr.interest_outstanding), 0) AS interest_outstanding
            FROM tmp_branch_wise_ageing bwa
            LEFT JOIN area_masters am
            ON UPPER(TRIM(am.area_code)) = UPPER(TRIM(bwa.area_code))
            LEFT JOIN tmp_contract_report tcr
            ON UPPER(TRIM(tcr.area_code)) = UPPER(TRIM(bwa.area_code))
            WHERE (? IS NULL OR UPPER(TRIM(COALESCE(bwa.area_code, ''))) = ?)
            GROUP BY
                bwa.area_code,
                NULLIF(TRIM(am.area_name), ''),
                bwa.no_of_accounts,
                bwa.aum,
                bwa.current_amount,
                bwa.days_1_30,
                bwa.days_31_60,
                bwa.days_61_90,
                bwa.days_91_120,
                bwa.days_121_150,
                bwa.days_151_180,
                bwa.days_above_180,
                bwa.total_outstanding
            ORDER BY bwa.area_code
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
                            rs.getString("area_name"),
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
                    return filterBranchRows(result, accessScope);
                }
            }
        });
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        LOGGER.info("Branch Wise Ageing procedure completed in {} ms for date {} and area {}", elapsedMs, asOnDate, areaCode);
        return rows == null ? List.of() : rows;
    }

    public List<ProcedureContractReportRow> getContractReportRows(LocalDate asOnDate, String areaCode) {
        return getContractReportRows(asOnDate, areaCode, new ReportAccessScope(false));
    }

    public List<ProcedureContractReportRow> getContractReportRows(LocalDate asOnDate, String areaCode, ReportAccessScope accessScope) {
        long startedAt = System.nanoTime();
        List<ProcedureContractReportRow> rows = jdbcTemplate.execute((ConnectionCallback<List<ProcedureContractReportRow>>) (connection) -> {
            String normalizedArea = clean(areaCode);
            try (var call = connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?, ?)")) {
                call.setObject(1, asOnDate);
                call.setString(2, normalizedArea);
                call.setString(3, userGroup(accessScope));
                call.execute();
            }

            String sql = """
                WITH last_paid AS (
                    SELECT
                        UPPER(TRIM(vd.sub_ledger_code)) AS contract_number,
                        MAX(vh.voucher_date) AS last_paid_emi_date
                    FROM voucher_headers vh
                    JOIN voucher_details vd
                      ON vd.voucher_header_id = vh.voucher_header_id
                    JOIN tmp_contract_report report_contract
                      ON UPPER(TRIM(report_contract.contract_number)) = UPPER(TRIM(vd.sub_ledger_code))
                    WHERE vh.voucher_date <= ?
                      AND UPPER(TRIM(COALESCE(vh.status, ''))) = 'AUTHORISED'
                      AND UPPER(TRIM(COALESCE(vh.voucher_type, ''))) <> 'HJ'
                      AND TRIM(COALESCE(vd.ledger_code, '')) IN ('3001', '4201')
                      AND NULLIF(TRIM(vd.sub_ledger_code), '') IS NOT NULL
                    GROUP BY UPPER(TRIM(vd.sub_ledger_code))
                ),
                flags AS (
                    SELECT
                        cf.contract_id,
                        COUNT(*)::int AS flag_count,
                        STRING_AGG(cfm.flag_name, ', ' ORDER BY cfm.display_order, cfm.flag_name) AS flag_names
                    FROM contract_flags cf
                    JOIN contract_flag_master cfm
                      ON cfm.contract_flag_master_id = cf.contract_flag_master_id
                    WHERE cfm.is_active = TRUE
                    GROUP BY cf.contract_id
                ),
                follow_ups AS (
                    SELECT
                        contract_id,
                        COUNT(*)::int AS follow_up_count
                    FROM contract_follow_ups
                    GROUP BY contract_id
                )
                SELECT
                    r.contract_id,
                    r.contract_number,
                    r.contract_type,
                    NULLIF(TRIM(c.area_code), '') AS area_code,
                    NULLIF(TRIM(am.area_name), '') AS area_name,
                    r.borrower_code,
                    COALESCE(NULLIF(TRIM(pm.full_name), ''), NULLIF(TRIM(r.borrower_code), '')) AS borrower_name,
                    NULLIF(TRIM(pm.contact_number), '') AS borrower_phone,
                    NULLIF(TRIM(CONCAT_WS(', ', NULLIF(TRIM(pm.address_line_1), ''), NULLIF(TRIM(pm.address_line_2), ''), NULLIF(TRIM(pm.area), ''), NULLIF(TRIM(pm.city), ''), NULLIF(TRIM(pm.state), ''), NULLIF(TRIM(pm.pin_code), ''))), '') AS borrower_address,
                    r.guarantor_code,
                    COALESCE(NULLIF(TRIM(gm.full_name), ''), NULLIF(TRIM(r.guarantor_code), '')) AS guarantor_name,
                    NULLIF(TRIM(gm.contact_number), '') AS guarantor_phone,
                    NULLIF(TRIM(CONCAT_WS(', ', NULLIF(TRIM(gm.address_line_1), ''), NULLIF(TRIM(gm.address_line_2), ''), NULLIF(TRIM(gm.area), ''), NULLIF(TRIM(gm.city), ''), NULLIF(TRIM(gm.state), ''), NULLIF(TRIM(gm.pin_code), ''))), '') AS guarantor_address,
                    NULLIF(TRIM(c.guarantor_2_code), '') AS guarantor_2_code,
                    COALESCE(NULLIF(TRIM(g2m.full_name), ''), NULLIF(TRIM(c.guarantor_2_code), '')) AS guarantor_2_name,
                    NULLIF(TRIM(g2m.contact_number), '') AS guarantor_2_phone,
                    NULLIF(TRIM(CONCAT_WS(', ', NULLIF(TRIM(g2m.address_line_1), ''), NULLIF(TRIM(g2m.address_line_2), ''), NULLIF(TRIM(g2m.area), ''), NULLIF(TRIM(g2m.city), ''), NULLIF(TRIM(g2m.state), ''), NULLIF(TRIM(g2m.pin_code), ''))), '') AS guarantor_2_address,
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
                    lp.last_paid_emi_date,
                    COALESCE(flags.flag_count, 0) AS flag_count,
                    COALESCE(flags.flag_names, '') AS flag_names,
                    COALESCE(follow_ups.follow_up_count, 0) AS follow_up_count,
                    r.ageing_bucket
                FROM tmp_contract_report r
                LEFT JOIN contracts c
                  ON c.contract_id = r.contract_id
                LEFT JOIN area_masters am
                  ON UPPER(TRIM(am.area_code)) = UPPER(TRIM(c.area_code))
                LEFT JOIN party_masters pm
                  ON UPPER(TRIM(pm.party_code)) = UPPER(TRIM(r.borrower_code))
                 AND pm.is_active = TRUE
                LEFT JOIN party_masters gm
                  ON UPPER(TRIM(gm.party_code)) = UPPER(TRIM(r.guarantor_code))
                 AND gm.is_active = TRUE
                LEFT JOIN party_masters g2m
                  ON UPPER(TRIM(g2m.party_code)) = UPPER(TRIM(c.guarantor_2_code))
                 AND g2m.is_active = TRUE
                LEFT JOIN last_paid lp
                  ON lp.contract_number = UPPER(TRIM(r.contract_number))
                LEFT JOIN flags
                  ON flags.contract_id = r.contract_id
                LEFT JOIN follow_ups
                  ON follow_ups.contract_id = r.contract_id
                WHERE (? IS NULL OR UPPER(TRIM(COALESCE(r.area_code, ''))) = ?)
                ORDER BY r.area_code, r.contract_number
                """;
            try (var select = connection.prepareStatement(sql)) {
                select.setObject(1, asOnDate);
                select.setString(2, normalizedArea);
                select.setString(3, normalizedArea);
                try (ResultSet rs = select.executeQuery()) {
                    List<ProcedureContractReportRow> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(new ProcedureContractReportRow(
                            rs.getLong("contract_id"),
                            rs.getString("contract_number"),
                            rs.getString("contract_type"),
                            rs.getString("area_code"),
                            rs.getString("area_name"),
                            rs.getString("borrower_code"),
                            rs.getString("borrower_name"),
                            rs.getString("borrower_phone"),
                            rs.getString("borrower_address"),
                            rs.getString("guarantor_code"),
                            rs.getString("guarantor_name"),
                            rs.getString("guarantor_phone"),
                            rs.getString("guarantor_address"),
                            rs.getString("guarantor_2_code"),
                            rs.getString("guarantor_2_name"),
                            rs.getString("guarantor_2_phone"),
                            rs.getString("guarantor_2_address"),
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
                            rs.getObject("last_paid_emi_date", LocalDate.class),
                            rs.getInt("flag_count"),
                            rs.getString("flag_names"),
                            rs.getInt("follow_up_count"),
                            rs.getString("ageing_bucket")
                        ));
                    }
                    return filterContractRows(result, accessScope);
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

    private List<AgingAnalysisBranchRowDto> filterBranchRows(List<AgingAnalysisBranchRowDto> rows, ReportAccessScope accessScope) {
        if (hasFullDataAccess(accessScope)) {
            return rows;
        }
        if (isUserType(accessScope, "USER")) {
            return rows;
        }
        List<String> areas = allowedAreaCodes(accessScope);
        if (areas.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = normalizedSet(areas);
        return rows.stream()
            .filter(row -> allowed.contains(normalize(row.areaCode())))
            .toList();
    }

    private List<ProcedureContractReportRow> filterContractRows(List<ProcedureContractReportRow> rows, ReportAccessScope accessScope) {
        if (hasFullDataAccess(accessScope)) {
            return rows;
        }
        if (isUserType(accessScope, "USER")) {
            return rows;
        }
        List<String> contracts = allowedContractNumbers(accessScope);
        if (!contracts.isEmpty()) {
            Set<String> allowed = normalizedSet(contracts);
            return rows.stream()
                .filter(row -> allowed.contains(normalize(row.contractNumber())))
                .toList();
        }
        List<String> areas = allowedAreaCodes(accessScope);
        if (areas.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = normalizedSet(areas);
        return rows.stream()
            .filter(row -> allowed.contains(normalize(row.areaCode())))
            .toList();
    }

    private boolean hasFullDataAccess(ReportAccessScope accessScope) {
        if (accessScope == null || accessScope.fullAccess() || accessScope.userId() == null) {
            return true;
        }
        String userType = jdbcTemplate.query(
            "SELECT UPPER(TRIM(COALESCE(user_type, 'USER'))) FROM users WHERE user_id = ?",
            ps -> ps.setLong(1, accessScope.userId()),
            rs -> rs.next() ? rs.getString(1) : "USER"
        );
        return false;
    }

    private boolean isUserType(ReportAccessScope accessScope, String expectedType) {
        if (accessScope == null || accessScope.userId() == null) {
            return false;
        }
        String userType = jdbcTemplate.query(
            "SELECT UPPER(TRIM(COALESCE(user_type, 'USER'))) FROM users WHERE user_id = ?",
            ps -> ps.setLong(1, accessScope.userId()),
            rs -> rs.next() ? rs.getString(1) : "USER"
        );
        return expectedType.equals(userType);
    }

    private String userGroup(ReportAccessScope accessScope) {
        return accessScope == null ? null : accessScope.userGroup();
    }

    private List<String> allowedAreaCodes(ReportAccessScope accessScope) {
        if (accessScope == null || accessScope.userId() == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
            "SELECT area_code FROM user_areas WHERE user_id = ?",
            String.class,
            accessScope.userId()
        );
    }

    private List<String> allowedContractNumbers(ReportAccessScope accessScope) {
        if (accessScope == null || accessScope.userId() == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
            "SELECT contract_number FROM user_contracts WHERE user_id = ?",
            String.class,
            accessScope.userId()
        );
    }

    private Set<String> normalizedSet(List<String> values) {
        return values.stream()
            .map(this::normalize)
            .filter(value -> value != null)
            .collect(java.util.stream.Collectors.toSet());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
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
        String areaName,
        String borrowerCode,
        String borrowerName,
        String borrowerPhone,
        String borrowerAddress,
        String guarantorCode,
        String guarantorName,
        String guarantorPhone,
        String guarantorAddress,
        String guarantor2Code,
        String guarantor2Name,
        String guarantor2Phone,
        String guarantor2Address,
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
        LocalDate lastPaidEmiDate,
        Integer flagCount,
        String flagNames,
        Integer followUpCount,
        String ageingBucket
    ) {
    }
}
