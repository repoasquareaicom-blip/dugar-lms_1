package dugar_lms_api.modules.accounts.repository;

import dugar_lms_api.modules.accounts.dto.VoucherDetailRequest;
import dugar_lms_api.modules.accounts.dto.VoucherDetailDto;
import dugar_lms_api.modules.accounts.dto.VoucherDto;
import dugar_lms_api.modules.accounts.dto.VoucherSaveRequest;
import dugar_lms_api.modules.accounts.dto.VoucherSaveResponse;
import dugar_lms_api.modules.accounts.dto.VoucherSummaryDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Repository
public class VoucherRepository {

    private static final RowMapper<VoucherSummaryDto> SUMMARY_ROW_MAPPER = (rs, rowNum) -> new VoucherSummaryDto(
        rs.getLong("voucher_header_id"),
        rs.getString("voucher_type"),
        rs.getString("voucher_number"),
        rs.getObject("voucher_date", LocalDate.class),
        rs.getString("transaction_type"),
        rs.getBigDecimal("voucher_amount"),
        rs.getString("contract_number"),
        rs.getString("header_control_code"),
        rs.getString("header_control_name"),
        rs.getInt("detail_count")
    );

    private static final RowMapper<VoucherDto> HEADER_ROW_MAPPER = (rs, rowNum) -> new VoucherDto(
        rs.getLong("voucher_header_id"),
        rs.getString("voucher_type"),
        rs.getString("voucher_type_description"),
        rs.getString("voucher_number"),
        rs.getObject("voucher_date", LocalDate.class),
        rs.getObject("system_date", LocalDate.class),
        rs.getString("transaction_type"),
        rs.getBigDecimal("voucher_amount"),
        rs.getString("contract_number"),
        rs.getObject("contract_id", Long.class),
        rs.getString("header_control_code"),
        rs.getString("header_control_name"),
        List.of()
    );

    private static final RowMapper<VoucherDetailDto> DETAIL_ROW_MAPPER = (rs, rowNum) -> new VoucherDetailDto(
        rs.getLong("voucher_detail_id"),
        rs.getInt("serial_number"),
        rs.getString("category"),
        rs.getString("ledger_code"),
        rs.getString("ledger_name"),
        rs.getBigDecimal("debit_amount"),
        rs.getBigDecimal("credit_amount"),
        rs.getString("party_code"),
        rs.getString("party_name"),
        rs.getString("loan_reference"),
        rs.getString("narration"),
        rs.getString("address")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public VoucherRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public VoucherSaveResponse save(VoucherSaveRequest request, Long userId) {
        Long headerId = jdbcTemplate.queryForObject(
            "SELECT nextval('voucher_headers_voucher_header_id_seq')",
            new MapSqlParameterSource(),
            Long.class
        );
        String voucherType = clean(request.voucherType()).toUpperCase(Locale.ROOT);
        String voucherNumber = voucherNumber(request.voucherNumber(), voucherType, headerId);

        jdbcTemplate.update(
            """
            INSERT INTO voucher_headers (
                voucher_header_id,
                voucher_type,
                voucher_type_description,
                voucher_number,
                voucher_date,
                system_date,
                transaction_type,
                voucher_amount,
                contract_number,
                contract_type,
                contract_id,
                bank_code,
                header_control_code,
                header_control_name,
                remarks,
                created_by,
                updated_by,
                updated_at
            )
            VALUES (
                :headerId,
                :voucherType,
                :voucherTypeDescription,
                :voucherNumber,
                :voucherDate,
                :systemDate,
                :transactionType,
                :voucherAmount,
                :contractNumber,
                :contractType,
                :contractId,
                :bankCode,
                :headerControlCode,
                :headerControlName,
                :remarks,
                :userId,
                :userId,
                CURRENT_TIMESTAMP
            )
            """,
            new MapSqlParameterSource()
                .addValue("headerId", headerId)
                .addValue("voucherType", voucherType)
                .addValue("voucherTypeDescription", clean(request.voucherTypeDescription()))
                .addValue("voucherNumber", voucherNumber)
                .addValue("voucherDate", request.voucherDate())
                .addValue("systemDate", request.systemDate())
                .addValue("transactionType", clean(request.transactionType()))
                .addValue("voucherAmount", request.voucherAmount())
                .addValue("contractNumber", clean(request.contractNumber()))
                .addValue("contractType", clean(request.contractType()))
                .addValue("contractId", request.contractId())
                .addValue("bankCode", clean(request.bankCode()))
                .addValue("headerControlCode", clean(request.headerControlCode()))
                .addValue("headerControlName", clean(request.headerControlName()))
                .addValue("remarks", clean(request.remarks()))
                .addValue("userId", userId)
        );

        for (VoucherDetailRequest detail : request.details()) {
            insertDetail(headerId, request.category(), detail, userId);
        }

        return new VoucherSaveResponse(
            headerId,
            voucherType,
            voucherNumber,
            request.voucherDate(),
            request.voucherAmount(),
            request.details().size()
        );
    }

    public List<VoucherSummaryDto> search(String voucherType, String transactionType, String voucherNumber, LocalDate voucherDate, String contractNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(
            """
            SELECT
                h.voucher_header_id,
                h.voucher_type,
                h.voucher_number,
                h.voucher_date,
                h.transaction_type,
                h.voucher_amount,
                h.contract_number,
                h.header_control_code,
                h.header_control_name,
                COUNT(d.voucher_detail_id)::int AS detail_count
            FROM voucher_headers h
            LEFT JOIN voucher_details d ON d.voucher_header_id = h.voucher_header_id
            WHERE 1 = 1
            """
        );

        String cleanedVoucherType = clean(voucherType);
        if (cleanedVoucherType != null) {
            sql.append(" AND UPPER(h.voucher_type) = UPPER(:voucherType)\n");
            params.addValue("voucherType", cleanedVoucherType);
        }

        String cleanedTransactionType = clean(transactionType);
        if (cleanedTransactionType != null) {
            sql.append(" AND UPPER(COALESCE(h.transaction_type, '')) = UPPER(:transactionType)\n");
            params.addValue("transactionType", cleanedTransactionType);
        }

        String voucherNumberPattern = pattern(voucherNumber);
        if (voucherNumberPattern != null) {
            sql.append(" AND h.voucher_number ILIKE :voucherNumber\n");
            params.addValue("voucherNumber", voucherNumberPattern);
        }

        if (voucherDate != null) {
            sql.append(" AND h.voucher_date = :voucherDate\n");
            params.addValue("voucherDate", voucherDate);
        }

        String contractNumberPattern = pattern(contractNumber);
        if (contractNumberPattern != null) {
            sql.append(" AND h.contract_number ILIKE :contractNumber\n");
            params.addValue("contractNumber", contractNumberPattern);
        }

        sql.append(
            """
             GROUP BY h.voucher_header_id
             ORDER BY h.voucher_date DESC NULLS LAST, h.voucher_header_id DESC
             LIMIT 100
            """
        );

        return jdbcTemplate.query(
            sql.toString(),
            params,
            SUMMARY_ROW_MAPPER
        );
    }

    public VoucherDto find(Long voucherHeaderId) {
        VoucherDto header = jdbcTemplate.queryForObject(
            """
            SELECT
                voucher_header_id,
                voucher_type,
                voucher_type_description,
                voucher_number,
                voucher_date,
                system_date,
                transaction_type,
                voucher_amount,
                contract_number,
                contract_id,
                header_control_code,
                header_control_name
            FROM voucher_headers
            WHERE voucher_header_id = :voucherHeaderId
            """,
            new MapSqlParameterSource("voucherHeaderId", voucherHeaderId),
            HEADER_ROW_MAPPER
        );
        List<VoucherDetailDto> details = jdbcTemplate.query(
            """
            SELECT
                voucher_detail_id,
                serial_number,
                category,
                ledger_code,
                ledger_name,
                debit_amount,
                credit_amount,
                party_code,
                party_name,
                loan_reference,
                narration,
                address
            FROM voucher_details
            WHERE voucher_header_id = :voucherHeaderId
            ORDER BY serial_number
            """,
            new MapSqlParameterSource("voucherHeaderId", voucherHeaderId),
            DETAIL_ROW_MAPPER
        );
        return new VoucherDto(
            header.voucherHeaderId(),
            header.voucherType(),
            header.voucherTypeDescription(),
            header.voucherNumber(),
            header.voucherDate(),
            header.systemDate(),
            header.transactionType(),
            header.voucherAmount(),
            header.contractNumber(),
            header.contractId(),
            header.headerControlCode(),
            header.headerControlName(),
            details
        );
    }

    public VoucherSaveResponse update(Long voucherHeaderId, VoucherSaveRequest request, Long userId) {
        String beforeSnapshot = snapshot(voucherHeaderId);
        jdbcTemplate.update(
            """
            UPDATE voucher_headers
            SET
                voucher_date = :voucherDate,
                system_date = :systemDate,
                transaction_type = :transactionType,
                voucher_amount = :voucherAmount,
                contract_number = :contractNumber,
                contract_type = :contractType,
                contract_id = :contractId,
                bank_code = :bankCode,
                header_control_code = :headerControlCode,
                header_control_name = :headerControlName,
                remarks = :remarks,
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE voucher_header_id = :voucherHeaderId
            """,
            new MapSqlParameterSource()
                .addValue("voucherHeaderId", voucherHeaderId)
                .addValue("voucherDate", request.voucherDate())
                .addValue("systemDate", request.systemDate())
                .addValue("transactionType", clean(request.transactionType()))
                .addValue("voucherAmount", request.voucherAmount())
                .addValue("contractNumber", clean(request.contractNumber()))
                .addValue("contractType", clean(request.contractType()))
                .addValue("contractId", request.contractId())
                .addValue("bankCode", clean(request.bankCode()))
                .addValue("headerControlCode", clean(request.headerControlCode()))
                .addValue("headerControlName", clean(request.headerControlName()))
                .addValue("remarks", clean(request.remarks()))
                .addValue("userId", userId)
        );
        jdbcTemplate.update(
            "DELETE FROM voucher_details WHERE voucher_header_id = :voucherHeaderId",
            new MapSqlParameterSource("voucherHeaderId", voucherHeaderId)
        );
        for (VoucherDetailRequest detail : request.details()) {
            insertDetail(voucherHeaderId, request.category(), detail, userId);
        }
        String afterSnapshot = snapshot(voucherHeaderId);
        audit(voucherHeaderId, "UPDATED", beforeSnapshot, afterSnapshot, userId);
        VoucherDto saved = find(voucherHeaderId);
        return new VoucherSaveResponse(saved.voucherHeaderId(), saved.voucherType(), saved.voucherNumber(), saved.voucherDate(), saved.voucherAmount(), saved.details().size());
    }

    public boolean activeLedgerExists(String ledgerCode) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM ledger_codes
                WHERE UPPER(ledger_code) = UPPER(:ledgerCode)
                  AND is_active = TRUE
            )
            """,
            new MapSqlParameterSource("ledgerCode", clean(ledgerCode)),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public boolean activeContractExists(String contractNumber) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM contracts
                WHERE (
                    UPPER(contract_number) = UPPER(:contractNumber)
                    OR UPPER(COALESCE(legacy_contract_number, '')) = UPPER(:contractNumber)
                )
                  AND is_active = TRUE
                  AND (is_draft IS NULL OR is_draft = FALSE)
            )
            """,
            new MapSqlParameterSource("contractNumber", clean(contractNumber)),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    private void insertDetail(Long headerId, String headerCategory, VoucherDetailRequest detail, Long userId) {
        jdbcTemplate.update(
            """
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
                address,
                created_by,
                updated_by,
                updated_at
            )
            VALUES (
                :headerId,
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
                :address,
                :userId,
                :userId,
                CURRENT_TIMESTAMP
            )
            """,
            new MapSqlParameterSource()
                .addValue("headerId", headerId)
                .addValue("serialNumber", detail.serialNumber())
                .addValue("category", clean(detail.category()) == null ? clean(headerCategory) : clean(detail.category()))
                .addValue("ledgerCode", clean(detail.ledgerCode()))
                .addValue("ledgerName", clean(detail.ledgerName()))
                .addValue("subLedgerCode", clean(detail.subLedgerCode()))
                .addValue("debitAmount", detail.debitAmount())
                .addValue("creditAmount", detail.creditAmount())
                .addValue("partyCode", clean(detail.partyCode()))
                .addValue("partyName", clean(detail.partyName()))
                .addValue("loanReference", clean(detail.loanReference()))
                .addValue("narration", clean(detail.narration()))
                .addValue("address", clean(detail.address()))
                .addValue("userId", userId)
        );
    }

    private String snapshot(Long voucherHeaderId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT jsonb_build_object(
                'header', to_jsonb(h),
                'details', COALESCE((
                    SELECT jsonb_agg(to_jsonb(d) ORDER BY d.serial_number)
                    FROM voucher_details d
                    WHERE d.voucher_header_id = h.voucher_header_id
                ), '[]'::jsonb)
            )::text
            FROM voucher_headers h
            WHERE h.voucher_header_id = :voucherHeaderId
            """,
            new MapSqlParameterSource("voucherHeaderId", voucherHeaderId),
            String.class
        );
    }

    private void audit(Long voucherHeaderId, String actionType, String beforeSnapshot, String afterSnapshot, Long userId) {
        jdbcTemplate.update(
            """
            INSERT INTO voucher_audit_trails (
                voucher_header_id,
                action_type,
                before_snapshot,
                after_snapshot,
                changed_by,
                remarks
            )
            VALUES (
                :voucherHeaderId,
                :actionType,
                :beforeSnapshot,
                :afterSnapshot,
                :userId,
                'Voucher edited from LMS UI'
            )
            """,
            new MapSqlParameterSource()
                .addValue("voucherHeaderId", voucherHeaderId)
                .addValue("actionType", actionType)
                .addValue("beforeSnapshot", beforeSnapshot)
                .addValue("afterSnapshot", afterSnapshot)
                .addValue("userId", userId)
        );
    }

    private String voucherNumber(String requestedNumber, String voucherType, Long headerId) {
        String cleanNumber = clean(requestedNumber);
        if (cleanNumber != null && !"AUTO".equalsIgnoreCase(cleanNumber)) {
            return cleanNumber;
        }
        String prefix = switch (voucherType) {
            case "PAYMENT" -> "PV";
            case "RECEIPT" -> "RV";
            case "JOURNAL" -> "JV";
            default -> "VCH";
        };
        return prefix + "-" + String.format("%06d", headerId);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String pattern(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : "%" + cleaned + "%";
    }
}
