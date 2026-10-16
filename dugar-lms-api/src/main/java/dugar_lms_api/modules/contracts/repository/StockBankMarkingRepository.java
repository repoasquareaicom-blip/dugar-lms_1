package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.StockBankMarkingDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class StockBankMarkingRepository {
    private final JdbcTemplate jdbc;
    public StockBankMarkingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SUMMARY = """
        SELECT c.contract_id, c.contract_number, c.contract_date, c.loan_amount, c.status,
            COALESCE(pm.full_name, c.borrower_code) AS borrower_name,
            COALESCE(a.vehicle_make, c.vehicle_make) AS vehicle_make,
            COALESCE(a.registration_number, c.registration_number) AS registration_number,
            b.id AS stock_marked_to_bank, b.bank_name
        FROM contracts c
        LEFT JOIN LATERAL (SELECT full_name FROM party_masters
            WHERE UPPER(TRIM(party_code)) = UPPER(TRIM(c.borrower_code)) AND is_active = TRUE
            ORDER BY full_name LIMIT 1) pm ON TRUE
        LEFT JOIN LATERAL (SELECT vehicle_make, registration_number FROM assets
            WHERE contract_id = c.contract_id ORDER BY asset_id LIMIT 1) a ON TRUE
        LEFT JOIN LATERAL (SELECT stock_marked_to_bank FROM contract_details
            WHERE contract_id = c.contract_id ORDER BY contract_detail_id LIMIT 1) cd ON TRUE
        LEFT JOIN bank_masters b ON b.id::text = cd.stock_marked_to_bank
        """;

    private List<StockBankMarkingDto> find(String condition, Object value) {
        return jdbc.query(SUMMARY + condition, (rs, n) -> new StockBankMarkingDto(
            rs.getLong("contract_id"), rs.getString("contract_number"), rs.getObject("contract_date", java.time.LocalDate.class),
            rs.getString("borrower_name"), rs.getBigDecimal("loan_amount"), rs.getString("vehicle_make"),
            rs.getString("registration_number"), rs.getString("status"), rs.getObject("stock_marked_to_bank", Long.class),
            rs.getString("bank_name")), value);
    }
    public List<StockBankMarkingDto> byNumber(String number) {
        return find(" WHERE c.is_active = TRUE AND UPPER(TRIM(c.contract_number)) = UPPER(TRIM(?))", number);
    }
    public StockBankMarkingDto byId(Long id) {
        return find(" WHERE c.contract_id = ?", id).stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Contract not found."));
    }
    public void lockContract(Long id) {
        if (jdbc.queryForList("SELECT contract_id FROM contracts WHERE contract_id = ? FOR UPDATE", Long.class, id).isEmpty())
            throw new IllegalArgumentException("Contract not found.");
    }
    public void lockActiveContract(Long id) {
        if (jdbc.queryForList("SELECT contract_id FROM contracts WHERE contract_id = ? AND is_active = TRUE FOR UPDATE", Long.class, id).isEmpty())
            throw new IllegalArgumentException("Active contract not found.");
    }
    public void validateBank(Long bankId, Long currentId) {
        if (bankId == null) return; // Contract Add/Edit supports an unassigned bank.
        var active = jdbc.queryForList("SELECT is_active FROM bank_masters WHERE id = ? FOR SHARE", Boolean.class, bankId);
        if (active.isEmpty() || (!Boolean.TRUE.equals(active.getFirst()) && !bankId.equals(currentId)))
            throw new IllegalArgumentException("Select an active bank. An existing inactive assignment may be retained.");
    }
    public void save(Long contractId, Long bankId, String user) {
        var ids = jdbc.queryForList("SELECT contract_detail_id FROM contract_details WHERE contract_id = ? ORDER BY contract_detail_id LIMIT 1", Long.class, contractId);
        if (ids.isEmpty()) {
            jdbc.update("INSERT INTO contract_details (contract_id, stock_marked_to_bank, created_by, is_active) VALUES (?, ?, ?, TRUE)", contractId, bankId == null ? null : bankId.toString(), user);
        } else {
            jdbc.update("UPDATE contract_details SET stock_marked_to_bank = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE contract_detail_id = ?", bankId == null ? null : bankId.toString(), user, ids.getFirst());
        }
    }
}
