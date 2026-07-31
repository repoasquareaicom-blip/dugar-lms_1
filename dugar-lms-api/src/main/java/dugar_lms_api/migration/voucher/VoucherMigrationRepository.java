package dugar_lms_api.migration.voucher;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
public class VoucherMigrationRepository {

    private static final String FIND_CONTRACT_SQL = """
        SELECT contract_id
        FROM contracts
        WHERE contract_type = :contractType
          AND contract_number = :contractNumber
        """;

    private static final String EXISTS_HEADER_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM voucher_headers
            WHERE voucher_type = :voucherType
              AND voucher_number = :voucherNumber
        )
        """;

    private static final String EXISTS_DETAIL_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM voucher_details
            WHERE voucher_header_id = :voucherHeaderId
              AND serial_number = :serialNumber
        )
        """;

    private static final String INSERT_HEADER_SQL = """
        INSERT INTO voucher_headers (
            voucher_type,
            voucher_type_description,
            voucher_number,
            voucher_date,
            system_date,
            transaction_type,
            voucher_amount,
            receipt_number,
            temporary_receipt_number,
            temporary_receipt_date,
            contract_number,
            contract_type,
            contract_id,
            bank_code,
            header_control_code,
            header_control_name,
            remarks
        )
        VALUES (
            :voucherType,
            :voucherTypeDescription,
            :voucherNumber,
            :voucherDate,
            :systemDate,
            :transactionType,
            :voucherAmount,
            :receiptNumber,
            :temporaryReceiptNumber,
            :temporaryReceiptDate,
            :contractNumber,
            :contractType,
            :contractId,
            :bankCode,
            :headerControlCode,
            :headerControlName,
            :remarks
        )
        RETURNING voucher_header_id
        """;

    private static final String INSERT_DETAIL_SQL = """
        INSERT INTO voucher_details (
            voucher_header_id,
            serial_number,
            category,
            ledger_code,
            ledger_name,
            sub_ledger_code,
            debit_amount,
            credit_amount,
            party_code,
            party_name,
            loan_reference,
            narration,
            address
        )
        VALUES (
            :voucherHeaderId,
            :serialNumber,
            :category,
            :ledgerCode,
            :ledgerName,
            :subLedgerCode,
            :debitAmount,
            :creditAmount,
            :partyCode,
            :partyName,
            :loanReference,
            :narration,
            :address
        )
        """;

    private static final String TABLE_COLUMNS_SQL = """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = :tableName
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public VoucherMigrationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> findContractIds(String contractType, String contractNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractType", contractType)
            .addValue("contractNumber", contractNumber);
        return jdbcTemplate.queryForList(FIND_CONTRACT_SQL, params, Long.class);
    }

    public boolean voucherHeaderExists(String voucherType, String voucherNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("voucherType", voucherType)
            .addValue("voucherNumber", voucherNumber);
        Boolean exists = jdbcTemplate.queryForObject(EXISTS_HEADER_SQL, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public boolean voucherDetailExists(Long voucherHeaderId, Integer serialNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("voucherHeaderId", voucherHeaderId)
            .addValue("serialNumber", serialNumber);
        Boolean exists = jdbcTemplate.queryForObject(EXISTS_DETAIL_SQL, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public Long insertHeader(VoucherHeaderInsert header) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("voucherType", header.voucherType())
            .addValue("voucherTypeDescription", header.voucherTypeDescription())
            .addValue("voucherNumber", header.voucherNumber())
            .addValue("voucherDate", header.voucherDate())
            .addValue("systemDate", header.systemDate())
            .addValue("transactionType", header.transactionType())
            .addValue("voucherAmount", header.voucherAmount())
            .addValue("receiptNumber", header.receiptNumber())
            .addValue("temporaryReceiptNumber", header.temporaryReceiptNumber())
            .addValue("temporaryReceiptDate", header.temporaryReceiptDate())
            .addValue("contractNumber", header.contractNumber())
            .addValue("contractType", header.contractType())
            .addValue("contractId", header.contractId())
            .addValue("bankCode", header.bankCode())
            .addValue("headerControlCode", header.headerControlCode())
            .addValue("headerControlName", header.headerControlName())
            .addValue("remarks", header.remarks());
        return jdbcTemplate.queryForObject(INSERT_HEADER_SQL, params, Long.class);
    }

    public void insertDetail(VoucherDetailInsert detail) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("voucherHeaderId", detail.voucherHeaderId())
            .addValue("serialNumber", detail.serialNumber())
            .addValue("category", detail.category())
            .addValue("ledgerCode", detail.ledgerCode())
            .addValue("ledgerName", detail.ledgerName())
            .addValue("subLedgerCode", detail.subLedgerCode())
            .addValue("debitAmount", detail.debitAmount())
            .addValue("creditAmount", detail.creditAmount())
            .addValue("partyCode", detail.partyCode())
            .addValue("partyName", detail.partyName())
            .addValue("loanReference", detail.loanReference())
            .addValue("narration", detail.narration())
            .addValue("address", detail.address());
        jdbcTemplate.update(INSERT_DETAIL_SQL, params);
    }

    public Set<String> tableColumns(String tableName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tableName", tableName);
        return new LinkedHashSet<>(jdbcTemplate.queryForList(TABLE_COLUMNS_SQL, params, String.class));
    }

    public record VoucherHeaderInsert(
        String voucherType,
        String voucherTypeDescription,
        String voucherNumber,
        LocalDate voucherDate,
        LocalDate systemDate,
        String transactionType,
        BigDecimal voucherAmount,
        String receiptNumber,
        String temporaryReceiptNumber,
        LocalDate temporaryReceiptDate,
        String contractNumber,
        String contractType,
        Long contractId,
        String bankCode,
        String headerControlCode,
        String headerControlName,
        String remarks
    ) {
    }

    public record VoucherDetailInsert(
        Long voucherHeaderId,
        Integer serialNumber,
        String category,
        String ledgerCode,
        String ledgerName,
        String subLedgerCode,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String partyCode,
        String partyName,
        String loanReference,
        String narration,
        String address
    ) {
    }
}
