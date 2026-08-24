package dugar_lms_api.modules.reports.afc;

import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class AfcReportRepository {

    private static final String SOURCE_SQL = """
        SELECT
            c.contract_id,
            COALESCE(NULLIF(TRIM(c.contract_number), ''), NULLIF(TRIM(c.legacy_contract_number), ''), c.contract_id::text) AS loan_number,
            NULLIF(TRIM(pm.full_name), '') AS customer_name,
            COALESCE(NULLIF(TRIM(a.product_type), ''), NULLIF(TRIM(c.contract_type), '')) AS product_type,
            COALESCE(c.loan_amount, 0) AS loan_amount,
            COALESCE(c.finance_charges, 0) AS finance_charges,
            COALESCE(c.total_contract_value, 0) AS contract_value,
            c.first_emi_date AS emi_start_date,
            COALESCE(NULLIF(TRIM(c.payment_frequency), ''), NULLIF(TRIM(c.repayment_terms), ''), NULLIF(TRIM(c.mode_of_payment), '')) AS payment_frequency
        FROM contracts c
        LEFT JOIN party_masters pm
          ON UPPER(TRIM(pm.party_code)) = UPPER(TRIM(c.borrower_code))
         AND pm.is_active = TRUE
        LEFT JOIN LATERAL (
            SELECT asset.product_type
            FROM assets asset
            WHERE asset.contract_id = c.contract_id
            ORDER BY asset.asset_id
            LIMIT 1
        ) a ON TRUE
        LEFT JOIN users created_user
          ON c.created_by = created_user.user_id::text
          OR LOWER(TRIM(COALESCE(c.created_by, ''))) = LOWER(TRIM(created_user.username))
        LEFT JOIN users updated_user
          ON c.updated_by = updated_user.user_id::text
          OR LOWER(TRIM(COALESCE(c.updated_by, ''))) = LOWER(TRIM(updated_user.username))
        WHERE c.is_active = TRUE
          AND (
              UPPER(TRIM(COALESCE(c.contract_number, ''))) = UPPER(TRIM(:loanNumber))
              OR UPPER(TRIM(COALESCE(c.legacy_contract_number, ''))) = UPPER(TRIM(:loanNumber))
          )
          AND (:areaCode IS NULL OR UPPER(TRIM(COALESCE(c.area_code, ''))) = UPPER(TRIM(:areaCode)))
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
        ORDER BY c.contract_id DESC
        LIMIT 1
        """;

    private static final String REPAYMENT_SQL = """
        SELECT
            contract_id,
            sequence_no,
            number_of_installments,
            installment_amount
        FROM contract_repayment_structures
        WHERE contract_id = :contractId
        ORDER BY sequence_no
        """;

    private static final String RECEIPTS_SQL = """
        SELECT
            vh.voucher_date AS receipt_date,
            COALESCE(NULLIF(TRIM(vh.receipt_number), ''), NULLIF(TRIM(vh.temporary_receipt_number), ''), NULLIF(TRIM(vh.voucher_number), ''), vh.voucher_header_id::text) AS receipt_number,
            GREATEST(COALESCE(vd.credit_amount, 0), 0) AS amount
        FROM voucher_headers vh
        JOIN voucher_details vd
          ON vd.voucher_header_id = vh.voucher_header_id
        WHERE UPPER(TRIM(COALESCE(vh.status, ''))) = 'AUTHORISED'
          AND vh.voucher_date <= :asOnDate
          AND TRIM(COALESCE(vd.ledger_code, '')) IN ('3001', '4201')
          AND UPPER(TRIM(COALESCE(vd.voucher_type, vh.voucher_type, ''))) <> 'HJ'
          AND GREATEST(COALESCE(vd.credit_amount, 0), 0) > 0
          AND (
              vh.contract_id = :contractId
              OR (
                  NULLIF(TRIM(vh.contract_number), '') IS NOT NULL
                  AND UPPER(TRIM(vh.contract_number)) IN (UPPER(TRIM(:loanNumber)), UPPER(TRIM(COALESCE(:legacyLoanNumber, ''))))
              )
              OR (
                  NULLIF(TRIM(vd.loan_reference), '') IS NOT NULL
                  AND UPPER(TRIM(vd.loan_reference)) IN (UPPER(TRIM(:loanNumber)), UPPER(TRIM(COALESCE(:legacyLoanNumber, ''))))
              )
              OR (
                  NULLIF(TRIM(vd.sub_ledger_code), '') IS NOT NULL
                  AND UPPER(TRIM(vd.sub_ledger_code)) IN (UPPER(TRIM(:loanNumber)), UPPER(TRIM(COALESCE(:legacyLoanNumber, ''))))
              )
          )
        ORDER BY vh.voucher_date, vh.voucher_header_id, vd.voucher_detail_id
        """;

    private static final RowMapper<AfcReportSource> SOURCE_MAPPER = (rs, rowNum) -> new AfcReportSource(
        rs.getLong("contract_id"),
        rs.getString("loan_number"),
        rs.getString("customer_name"),
        rs.getString("product_type"),
        rs.getBigDecimal("loan_amount"),
        rs.getBigDecimal("finance_charges"),
        rs.getBigDecimal("contract_value"),
        rs.getObject("emi_start_date", java.time.LocalDate.class),
        rs.getString("payment_frequency")
    );

    private static final RowMapper<AfcRepaymentSlab> REPAYMENT_MAPPER = (rs, rowNum) -> new AfcRepaymentSlab(
        rs.getLong("contract_id"),
        rs.getObject("sequence_no", Integer.class),
        rs.getObject("number_of_installments", Integer.class),
        rs.getBigDecimal("installment_amount")
    );

    private static final RowMapper<AfcReceipt> RECEIPT_MAPPER = (rs, rowNum) -> new AfcReceipt(
        rs.getObject("receipt_date", java.time.LocalDate.class),
        rs.getString("receipt_number"),
        rs.getBigDecimal("amount")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AfcReportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AfcReportSource> findSource(String loanNumber, String areaCode) {
        return findSource(loanNumber, areaCode, new ReportAccessScope(false));
    }

    public Optional<AfcReportSource> findSource(String loanNumber, String areaCode, ReportAccessScope accessScope) {
        List<AfcReportSource> rows = jdbcTemplate.query(
            SOURCE_SQL,
            new MapSqlParameterSource()
                .addValue("loanNumber", loanNumber, Types.VARCHAR)
                .addValue("areaCode", areaCode, Types.VARCHAR)
                .addValue("restricted", accessScope != null && accessScope.restrictedToUserGroup()),
            SOURCE_MAPPER
        );
        return rows.stream().findFirst();
    }

    public List<AfcRepaymentSlab> findRepaymentSlabs(Long contractId) {
        return jdbcTemplate.query(
            REPAYMENT_SQL,
            new MapSqlParameterSource("contractId", contractId),
            REPAYMENT_MAPPER
        );
    }

    public List<AfcReceipt> findReceipts(AfcReportSource source, java.time.LocalDate asOnDate) {
        return jdbcTemplate.query(
            RECEIPTS_SQL,
            new MapSqlParameterSource()
                .addValue("contractId", source.contractId())
                .addValue("loanNumber", source.loanNumber(), Types.VARCHAR)
                .addValue("legacyLoanNumber", source.loanNumber(), Types.VARCHAR)
                .addValue("asOnDate", asOnDate),
            RECEIPT_MAPPER
        );
    }
}
