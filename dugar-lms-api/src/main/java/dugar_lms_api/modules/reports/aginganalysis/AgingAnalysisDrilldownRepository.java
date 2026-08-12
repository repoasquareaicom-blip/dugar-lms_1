package dugar_lms_api.modules.reports.aginganalysis;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class AgingAnalysisDrilldownRepository {

    private static final String CONTRACT_SQL = """
        SELECT
            c.contract_id,
            COALESCE(NULLIF(TRIM(c.contract_number), ''), NULLIF(TRIM(c.legacy_contract_number), ''), c.contract_id::text) AS contract_number,
            NULLIF(TRIM(c.area_code), '') AS area_code,
            c.contract_date,
            c.first_emi_date,
            COALESCE(c.total_contract_value, 0) AS total_contract_value,
            COALESCE(c.finance_charges, 0) AS finance_charges,
            COALESCE(c.loan_amount, 0) AS loan_amount,
            COALESCE(NULLIF(TRIM(c.payment_frequency), ''), NULLIF(TRIM(c.repayment_terms), ''), NULLIF(TRIM(c.mode_of_payment), '')) AS payment_frequency
        FROM contracts c
        WHERE c.is_active = TRUE
          AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
          AND c.contract_id = :contractId
        """;

    private static final String REPAYMENT_SQL = """
        SELECT
            sequence_no,
            number_of_installments,
            installment_amount
        FROM contract_repayment_structures
        WHERE contract_id = :contractId
        ORDER BY sequence_no
        """;

    private static final String RECEIPTS_SQL = """
        SELECT
            vh.voucher_date,
            vh.voucher_number,
            vh.voucher_type,
            vh.receipt_number,
            vh.temporary_receipt_number,
            vd.ledger_code,
            vd.sub_ledger_code,
            COALESCE(vd.debit_amount, 0) AS debit_amount,
            COALESCE(vd.credit_amount, 0) AS credit_amount,
            GREATEST(COALESCE(vd.credit_amount, 0), 0) AS collection_amount,
            vd.narration,
            vh.authorised_at
        FROM voucher_headers vh
        JOIN voucher_details vd
          ON vd.voucher_header_id = vh.voucher_header_id
        WHERE UPPER(TRIM(COALESCE(vh.status, ''))) = 'AUTHORISED'
          AND UPPER(TRIM(COALESCE(vh.voucher_type, ''))) <> 'HJ'
          AND UPPER(TRIM(COALESCE(vd.voucher_type, vh.voucher_type, ''))) <> 'HJ'
          AND TRIM(COALESCE(vd.ledger_code, '')) IN ('3001', '4201')
          AND UPPER(TRIM(COALESCE(vd.sub_ledger_code, ''))) = UPPER(TRIM(:contractNumber))
          AND vh.voucher_date <= :asOnDate
        ORDER BY vh.voucher_date, vh.voucher_header_id, vd.voucher_detail_id
        """;

    private static final RowMapper<ContractSource> CONTRACT_MAPPER = (rs, rowNum) -> new ContractSource(
        rs.getLong("contract_id"),
        rs.getString("contract_number"),
        rs.getString("area_code"),
        rs.getObject("contract_date", LocalDate.class),
        rs.getObject("first_emi_date", LocalDate.class),
        rs.getBigDecimal("total_contract_value"),
        rs.getBigDecimal("finance_charges"),
        rs.getBigDecimal("loan_amount"),
        rs.getString("payment_frequency")
    );

    private static final RowMapper<RepaymentSlab> REPAYMENT_MAPPER = (rs, rowNum) -> new RepaymentSlab(
        rs.getObject("sequence_no", Integer.class),
        rs.getObject("number_of_installments", Integer.class),
        rs.getBigDecimal("installment_amount")
    );

    private static final RowMapper<AgingAnalysisReceiptDto> RECEIPT_MAPPER = (rs, rowNum) -> new AgingAnalysisReceiptDto(
        rs.getObject("voucher_date", LocalDate.class),
        rs.getString("voucher_number"),
        rs.getString("voucher_type"),
        rs.getString("receipt_number"),
        rs.getString("temporary_receipt_number"),
        rs.getString("ledger_code"),
        rs.getString("sub_ledger_code"),
        rs.getBigDecimal("debit_amount"),
        rs.getBigDecimal("credit_amount"),
        rs.getBigDecimal("collection_amount"),
        rs.getString("narration"),
        rs.getObject("authorised_at", java.time.LocalDateTime.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AgingAnalysisDrilldownRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ContractSource findContract(Long contractId) {
        List<ContractSource> rows = jdbcTemplate.query(CONTRACT_SQL, new MapSqlParameterSource("contractId", contractId), CONTRACT_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<RepaymentSlab> findRepaymentSlabs(Long contractId) {
        return jdbcTemplate.query(REPAYMENT_SQL, new MapSqlParameterSource("contractId", contractId), REPAYMENT_MAPPER);
    }

    public List<AgingAnalysisReceiptDto> findReceipts(String contractNumber, LocalDate asOnDate) {
        return jdbcTemplate.query(
            RECEIPTS_SQL,
            new MapSqlParameterSource()
                .addValue("contractNumber", contractNumber)
                .addValue("asOnDate", asOnDate),
            RECEIPT_MAPPER
        );
    }

    record ContractSource(
        Long contractId,
        String contractNumber,
        String areaCode,
        LocalDate contractDate,
        LocalDate firstEmiDate,
        BigDecimal totalContractValue,
        BigDecimal financeCharges,
        BigDecimal loanAmount,
        String paymentFrequency
    ) {
    }

    record RepaymentSlab(
        Integer sequenceNo,
        Integer numberOfInstallments,
        BigDecimal installmentAmount
    ) {
    }
}
