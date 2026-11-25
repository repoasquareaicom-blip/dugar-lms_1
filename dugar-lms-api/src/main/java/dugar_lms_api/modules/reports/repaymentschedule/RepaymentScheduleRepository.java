package dugar_lms_api.modules.reports.repaymentschedule;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static dugar_lms_api.modules.reports.repaymentschedule.RepaymentScheduleData.*;

@Repository
public class RepaymentScheduleRepository {
    private final JdbcTemplate jdbc;
    public RepaymentScheduleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Header> header(long contractId) {
        return jdbc.query("""
            SELECT c.contract_id, c.contract_number, p.full_name AS borrower_name,
                   c.contract_date, c.loan_amount, c.irr_rate, c.tenure_months, c.first_emi_date
            FROM public.contracts c
            LEFT JOIN LATERAL (
                SELECT pm.full_name FROM public.party_masters pm
                WHERE upper(trim(pm.party_code)) = upper(trim(c.borrower_code)) AND pm.is_active = TRUE
                ORDER BY pm.id LIMIT 1
            ) p ON TRUE
            WHERE c.contract_id = ?
            """, (rs, n) -> new Header(rs.getLong("contract_id"), rs.getString("contract_number"),
                rs.getString("borrower_name"), rs.getObject("contract_date", LocalDate.class),
                rs.getBigDecimal("loan_amount"), rs.getBigDecimal("irr_rate"),
                rs.getObject("tenure_months", Integer.class), rs.getObject("first_emi_date", LocalDate.class)),
            contractId).stream().findFirst();
    }

    public List<Slab> slabs(long contractId) {
        return jdbc.query("""
            SELECT sequence_no, number_of_installments, installment_amount
            FROM public.contract_repayment_structures WHERE contract_id = ? ORDER BY sequence_no
            """, (rs, n) -> new Slab(rs.getInt(1), rs.getInt(2), rs.getBigDecimal(3)), contractId);
    }

    Optional<NormalRepaymentSchedule.Details> normalDetails(long contractId) {
        return jdbc.query("""
            SELECT c.contract_number, c.contract_date, c.tenure_months, c.first_emi_date,
                   c.payment_frequency, c.repayment_terms, c.loan_amount, c.finance_charges,
                   c.bpfc_amount, c.insurance_deposit, c.total_contract_value,
                   COALESCE(NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(a.vehicle_make), ''),
                       NULLIF(TRIM(a.version), ''))), ''),
                       NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(c.vehicle_make), ''),
                       NULLIF(TRIM(c.equipment_model), ''))), '')) AS vehicle,
                   NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(p.salutation), ''),
                       NULLIF(TRIM(p.full_name), ''))), '') AS borrower_name,
                   p.swd_name, p.address_line_1, p.address_line_2, p.area, p.city,
                   COALESCE(NULLIF(TRIM(state_lookup.other_desc), ''), p.state) AS state, p.pin_code
            FROM public.contracts c
            LEFT JOIN LATERAL (
                SELECT pm.* FROM public.party_masters pm
                WHERE upper(trim(pm.party_code)) = upper(trim(c.borrower_code)) AND pm.is_active
                ORDER BY pm.id LIMIT 1
            ) p ON TRUE
            LEFT JOIN LATERAL (
                SELECT o.other_desc FROM public.g_others o
                WHERE trim(o.other_code) = trim(p.state)
                ORDER BY o.other_desc LIMIT 1
            ) state_lookup ON TRUE
            LEFT JOIN LATERAL (
                SELECT asset.vehicle_make, asset.version FROM public.assets asset
                WHERE asset.contract_id = c.contract_id ORDER BY asset.asset_id LIMIT 1
            ) a ON TRUE
            WHERE c.contract_id = ?
            """, (rs, n) -> new NormalRepaymentSchedule.Details(
                rs.getString("contract_number"), rs.getObject("contract_date", LocalDate.class),
                rs.getObject("tenure_months", Integer.class), rs.getObject("first_emi_date", LocalDate.class),
                rs.getString("payment_frequency"), rs.getString("repayment_terms"), rs.getString("vehicle"),
                rs.getString("borrower_name"), rs.getString("swd_name"), rs.getString("address_line_1"),
                rs.getString("address_line_2"), rs.getString("area"), rs.getString("city"),
                rs.getString("state"), rs.getString("pin_code"), rs.getBigDecimal("loan_amount"),
                rs.getBigDecimal("finance_charges"), rs.getBigDecimal("bpfc_amount"),
                rs.getBigDecimal("insurance_deposit"), rs.getBigDecimal("total_contract_value")),
            contractId).stream().findFirst();
    }

    public Totals totals(String contractNumber) {
        // Aggregate before rounding: displayed installment columns round independently.
        return jdbc.queryForObject("""
            SELECT sum(emi_amount), sum(interest_amount), sum(principal_amount)
            FROM public.fn_contract_repayment_schedule_unrounded(CAST(? AS VARCHAR))
            """, (rs, n) -> new Totals(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)),
            contractNumber);
    }

    public List<Row> schedule(String contractNumber) {
        return jdbc.query("""
            SELECT installment_no, due_date, opening_principal, emi_amount, interest_amount,
                   principal_amount, closing_principal, rounding_adjustment
            FROM public.fn_contract_repayment_schedule(CAST(? AS VARCHAR)) ORDER BY installment_no
            """, (rs, n) -> new Row(rs.getInt(1), rs.getObject(2, LocalDate.class),
                rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)), contractNumber);
    }
}
