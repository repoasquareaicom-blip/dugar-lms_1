package dugar_lms_api.modules.reports.stockstatement;

import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class StockStatementRepository {
    private final JdbcTemplate jdbc;

    public StockStatementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Bank(Long id, String code, String name) {}

    public Bank activeBank(Long id) {
        var banks = jdbc.query("SELECT id, bank_code, bank_name FROM bank_masters WHERE id = ? AND is_active = TRUE",
            (rs, n) -> new Bank(rs.getLong(1), rs.getString(2), rs.getString(3)), id);
        if (banks.isEmpty()) throw new IllegalArgumentException("Select an active bank.");
        return banks.getFirst();
    }

    public List<StockStatementRow> rows(Long bankId, LocalDate date, ReportAccessScope scope) {
        return jdbc.execute((ConnectionCallback<List<StockStatementRow>>) connection -> {
            // Procedure and its temporary-table reads must use the same connection.
            try (var call = connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?, ?)")) {
                call.setObject(1, date);
                call.setString(2, null);
                call.setString(3, scope.userGroup());
                call.execute();
            }
            try (var select = connection.prepareStatement(REPORT_SQL)) {
                select.setObject(1, date);
                select.setLong(2, bankId);
                select.setBoolean(3, scope.fullAccess());
                select.setObject(4, scope.userId());
                try (var rs = select.executeQuery()) {
                    List<StockStatementRow> rows = new ArrayList<>();
                    while (rs.next()) rows.add(new StockStatementRow(rs.getLong("contract_id"),
                        rs.getString("contract_number"), rs.getObject("contract_date", LocalDate.class),
                        StockStatementAddress.format(rs.getString("borrower_name"), rs.getString("borrower_address")),
                        rs.getString("vehicle_details"), rs.getBigDecimal("loan_amount"),
                        rs.getBigDecimal("principal_per_installment"), rs.getInt("total_emi"),
                        rs.getInt("balance_emi"), rs.getBigDecimal("principal_outstanding")));
                    return rows;
                }
            }
        });
    }

    static final String REPORT_SQL = """
        SELECT r.contract_id, r.contract_number, r.contract_date,
            COALESCE(NULLIF(TRIM(pm.full_name), ''), r.borrower_code) AS borrower_name,
            CONCAT_WS(', ', NULLIF(TRIM(pm.address_line_1), ''), NULLIF(TRIM(pm.address_line_2), ''),
                NULLIF(TRIM(pm.area), ''), NULLIF(TRIM(pm.city), ''),
                COALESCE(NULLIF(TRIM(state.other_desc), ''), NULLIF(TRIM(pm.state), '')),
                NULLIF(TRIM(pm.pin_code), '')) AS borrower_address,
            CONCAT_WS(E'\n', COALESCE(NULLIF(TRIM(asset.vehicle_make), ''), NULLIF(TRIM(c.vehicle_make), '')),
                COALESCE(NULLIF(TRIM(asset.registration_number), ''), NULLIF(TRIM(r.registration_number), ''))) AS vehicle_details,
            r.original_principal AS loan_amount,
            ROUND(COALESCE(emi.installment_amount * r.original_principal / NULLIF(r.total_contract_value, 0), 0), 2)
                AS principal_per_installment,
            COALESCE(emi.total_emi, 0) AS total_emi,
            COALESCE(emi.balance_emi, 0) AS balance_emi,
            r.principal_outstanding
        FROM tmp_contract_report r
        JOIN contracts c ON c.contract_id = r.contract_id
        LEFT JOIN party_masters pm ON UPPER(TRIM(pm.party_code)) = UPPER(TRIM(r.borrower_code)) AND pm.is_active = TRUE
        LEFT JOIN g_others state ON UPPER(TRIM(state.other_code)) = UPPER(TRIM(pm.state))
        LEFT JOIN LATERAL (
            SELECT a.vehicle_make, a.registration_number FROM assets a
            WHERE a.contract_id = r.contract_id ORDER BY a.asset_id LIMIT 1
        ) asset ON TRUE
        LEFT JOIN LATERAL (
            SELECT COUNT(*)::int AS total_emi,
                COUNT(*) FILTER (WHERE e.emi_outstanding > 0)::int AS balance_emi,
                COALESCE((ARRAY_AGG(e.installment_amount ORDER BY e.emi_no)
                    FILTER (WHERE e.emi_outstanding > 0))[1],
                    (ARRAY_AGG(e.installment_amount ORDER BY e.emi_no))[1], 0) AS installment_amount
            FROM tmp_emi_outstanding e WHERE e.contract_id = r.contract_id
        ) emi ON TRUE
        WHERE r.principal_outstanding > 0
          AND c.is_active = TRUE AND r.contract_date <= ?::date
          AND EXISTS (
              SELECT 1 FROM contract_details cd
              JOIN bank_masters bank ON bank.id::text = cd.stock_marked_to_bank
              WHERE cd.contract_id = r.contract_id AND cd.is_active = TRUE AND bank.is_active = TRUE AND bank.id = ?
          )
          AND (? OR EXISTS (
              SELECT 1 FROM users scope_user WHERE scope_user.user_id = ? AND (
                  UPPER(TRIM(COALESCE(scope_user.user_type, 'USER'))) = 'USER'
                  OR
                  UPPER(TRIM(scope_user.user_type)) = 'CUSTOMER' AND EXISTS (
                      SELECT 1 FROM user_contracts uc WHERE uc.user_id = scope_user.user_id
                      AND UPPER(TRIM(uc.contract_number)) = UPPER(TRIM(r.contract_number)))
                  OR UPPER(TRIM(scope_user.user_type)) IN ('BRANCH', 'STATE') AND EXISTS (
                      SELECT 1 FROM user_areas ua WHERE ua.user_id = scope_user.user_id
                      AND UPPER(TRIM(ua.area_code)) = UPPER(TRIM(r.area_code)))
              )
          ))
        ORDER BY r.contract_number, r.contract_id
        """;
}
