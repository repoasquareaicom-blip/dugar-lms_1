package dugar_lms_api.modules.reports.aginganalysis;

import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class AgingAnalysisDrilldownRepository {

    private static final String CONTRACT_SQL = """
        SELECT
            c.contract_id,
            COALESCE(NULLIF(TRIM(c.contract_number), ''), NULLIF(TRIM(c.legacy_contract_number), ''), c.contract_id::text) AS contract_number,
            NULLIF(TRIM(c.area_code), '') AS area_code,
            NULLIF(TRIM(am.area_name), '') AS area_name,
            c.contract_date,
            c.first_emi_date,
            COALESCE(c.total_contract_value, 0) AS total_contract_value,
            COALESCE(c.finance_charges, 0) AS finance_charges,
            COALESCE(c.loan_amount, 0) AS loan_amount,
            COALESCE(NULLIF(TRIM(c.payment_frequency), ''), NULLIF(TRIM(c.repayment_terms), ''), NULLIF(TRIM(c.mode_of_payment), '')) AS payment_frequency
        FROM contracts c
        LEFT JOIN area_masters am
          ON UPPER(TRIM(am.area_code)) = UPPER(TRIM(c.area_code))
        WHERE c.is_active = TRUE
          AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
          AND c.contract_id = :contractId
          AND (
              :restricted = FALSE
              OR EXISTS (
                  SELECT 1
                  FROM users access_user
                  WHERE access_user.user_id::text = TRIM(COALESCE(c.updated_by, ''))
                    AND LOWER(TRIM(COALESCE(access_user.user_group, ''))) = 'user'
              )
          )
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

    private static final String TABLE_LOOKUP_SQL = """
        SELECT table_schema, table_name
        FROM information_schema.tables
        WHERE LOWER(table_name) = :tableName
          AND table_type = 'BASE TABLE'
        ORDER BY CASE table_schema WHEN CURRENT_SCHEMA() THEN 0 WHEN 'public' THEN 1 ELSE 2 END
        LIMIT 1
        """;

    private static final String COLUMN_LOOKUP_SQL = """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = :tableSchema
          AND table_name = :tableName
        ORDER BY ordinal_position
        """;

    private static final String NORMALIZED_HEADER_SQL = """
        SELECT *
        FROM voucher_headers
        WHERE UPPER(TRIM(COALESCE(voucher_number, ''))) = UPPER(TRIM(:voucherNumber))
          AND (:voucherType IS NULL OR UPPER(TRIM(COALESCE(voucher_type, ''))) = UPPER(TRIM(:voucherType)))
          AND (:contractNumber IS NULL OR UPPER(TRIM(COALESCE(contract_number, ''))) = UPPER(TRIM(:contractNumber)))
        ORDER BY voucher_header_id
        """;

    private static final String NORMALIZED_DETAIL_SQL = """
        SELECT vd.*
        FROM voucher_details vd
        JOIN voucher_headers vh
          ON vh.voucher_header_id = vd.voucher_header_id
        WHERE UPPER(TRIM(COALESCE(vh.voucher_number, ''))) = UPPER(TRIM(:voucherNumber))
          AND (:voucherType IS NULL OR UPPER(TRIM(COALESCE(vh.voucher_type, ''))) = UPPER(TRIM(:voucherType)))
          AND (:contractNumber IS NULL OR UPPER(TRIM(COALESCE(vh.contract_number, ''))) = UPPER(TRIM(:contractNumber)))
        ORDER BY vd.serial_number, vd.voucher_detail_id
        """;

    private static final RowMapper<ContractSource> CONTRACT_MAPPER = (rs, rowNum) -> new ContractSource(
        rs.getLong("contract_id"),
        rs.getString("contract_number"),
        rs.getString("area_code"),
        rs.getString("area_name"),
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
        return findContract(contractId, new ReportAccessScope(false));
    }

    public ContractSource findContract(Long contractId, ReportAccessScope accessScope) {
        boolean restricted = accessScope != null && accessScope.restrictedToUserGroup();
        List<ContractSource> rows = jdbcTemplate.query(
            CONTRACT_SQL,
            new MapSqlParameterSource()
                .addValue("contractId", contractId)
                .addValue("restricted", restricted),
            CONTRACT_MAPPER
        );
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

    public AgingAnalysisRawVoucherDto findRawVoucher(String contractNumber, String voucherType, String voucherNumber) {
        String cleanVoucherNumber = clean(voucherNumber);
        if (cleanVoucherNumber == null) {
            throw new IllegalArgumentException("Voucher No is required");
        }
        String cleanVoucherType = clean(voucherType);
        String cleanContractNumber = clean(contractNumber);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("voucherNumber", cleanVoucherNumber)
            .addValue("voucherType", cleanVoucherType)
            .addValue("contractNumber", cleanContractNumber);

        List<Map<String, Object>> headers = queryLegacyRows("hp_vrhdr", params, List.of(
            condition("voucher_no", "voucherNumber", true),
            condition("voucher_type", "voucherType", false)
        ), List.of("voucher_date", "voucher_no"));
        List<Map<String, Object>> details = queryLegacyRows("hp_vrdtl", params, List.of(
            condition("voucher_no", "voucherNumber", true),
            condition("voucher_type", "voucherType", false)
        ), List.of("sl_no", "voucher_no"));

        if (headers == null) {
            headers = jdbcTemplate.queryForList(NORMALIZED_HEADER_SQL, params);
        }
        if (details == null) {
            details = jdbcTemplate.queryForList(NORMALIZED_DETAIL_SQL, params);
        }
        return new AgingAnalysisRawVoucherDto(headers, details);
    }

    private List<Map<String, Object>> queryLegacyRows(
        String tableName,
        MapSqlParameterSource params,
        List<Condition> conditions,
        List<String> orderColumns
    ) {
        TableRef table = findTable(tableName);
        if (table == null) {
            return null;
        }
        List<String> columns = columns(table);
        String where = conditions.stream()
            .filter(condition -> columns.stream().anyMatch(column -> column.equalsIgnoreCase(condition.column())))
            .map(condition -> {
                String column = columns.stream().filter(value -> value.equalsIgnoreCase(condition.column())).findFirst().orElse(condition.column());
                String comparison = "UPPER(TRIM(COALESCE(" + quote(column) + "::text, ''))) = UPPER(TRIM(:" + condition.parameter() + "))";
                return condition.required() ? comparison : "(:" + condition.parameter() + " IS NULL OR " + comparison + ")";
            })
            .reduce((left, right) -> left + " AND " + right)
            .orElse("1 = 1");
        String orderBy = orderColumns.stream()
            .filter(orderColumn -> columns.stream().anyMatch(column -> column.equalsIgnoreCase(orderColumn)))
            .map(orderColumn -> columns.stream().filter(value -> value.equalsIgnoreCase(orderColumn)).findFirst().orElse(orderColumn))
            .map(this::quote)
            .reduce((left, right) -> left + ", " + right)
            .map(value -> " ORDER BY " + value)
            .orElse("");
        return jdbcTemplate.queryForList("SELECT * FROM " + table.qualifiedName() + " WHERE " + where + orderBy, params);
    }

    private TableRef findTable(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            TABLE_LOOKUP_SQL,
            new MapSqlParameterSource("tableName", tableName.toLowerCase())
        );
        if (rows.isEmpty()) {
            return null;
        }
        return new TableRef(String.valueOf(rows.get(0).get("table_schema")), String.valueOf(rows.get(0).get("table_name")));
    }

    private List<String> columns(TableRef table) {
        return jdbcTemplate.queryForList(
            COLUMN_LOOKUP_SQL,
            new MapSqlParameterSource()
                .addValue("tableSchema", table.schema())
                .addValue("tableName", table.name()),
            String.class
        );
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private Condition condition(String column, String parameter, boolean required) {
        return new Condition(column, parameter, required);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record ContractSource(
        Long contractId,
        String contractNumber,
        String areaCode,
        String areaName,
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

    private record TableRef(String schema, String name) {
        String qualifiedName() {
            return "\"" + schema.replace("\"", "\"\"") + "\".\"" + name.replace("\"", "\"\"") + "\"";
        }
    }

    private record Condition(String column, String parameter, boolean required) {
    }
}
