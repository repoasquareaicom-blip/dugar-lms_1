package dugar_lms_api.modules.accounts.repository;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accounts.dto.VoucherAuthorisationCriteria;
import dugar_lms_api.modules.accounts.dto.VoucherDetailRequest;
import dugar_lms_api.modules.accounts.dto.VoucherDetailDto;
import dugar_lms_api.modules.accounts.dto.VoucherDto;
import dugar_lms_api.modules.accounts.dto.VoucherHistoryDto;
import dugar_lms_api.modules.accounts.dto.VoucherReviewDto;
import dugar_lms_api.modules.accounts.dto.VoucherSaveRequest;
import dugar_lms_api.modules.accounts.dto.VoucherSaveResponse;
import dugar_lms_api.modules.accounts.dto.VoucherSummaryDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        rs.getString("status"),
        rs.getObject("version_number", Integer.class),
        rs.getObject("submitted_by", Long.class),
        rs.getObject("submitted_at", LocalDateTime.class),
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
        rs.getString("remarks"),
        rs.getString("status"),
        rs.getObject("version_number", Integer.class),
        List.of()
    );

    private static final RowMapper<VoucherHistoryDto> HISTORY_ROW_MAPPER = (rs, rowNum) -> new VoucherHistoryDto(
        rs.getLong("voucher_header_history_id"),
        rs.getLong("voucher_header_id"),
        rs.getObject("version_number", Integer.class),
        rs.getString("previous_status"),
        rs.getObject("changed_by", Long.class),
        rs.getObject("changed_at", LocalDateTime.class),
        rs.getString("change_reason")
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
                status,
                submitted_by,
                submitted_at,
                version_number,
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
                'SUBMITTED',
                :userId,
                CURRENT_TIMESTAMP,
                1,
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
                h.status,
                h.version_number,
                h.submitted_by,
                h.submitted_at,
                COUNT(d.voucher_detail_id)::int AS detail_count
            FROM voucher_headers h
            LEFT JOIN voucher_details d ON d.voucher_header_id = h.voucher_header_id
            WHERE h.status = 'AUTHORISED'
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
                header_control_name,
                remarks,
                status,
                version_number
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
            header.remarks(),
            header.status(),
            header.versionNumber(),
            details
        );
    }

    public VoucherSaveResponse update(Long voucherHeaderId, VoucherSaveRequest request, Long userId) {
        StatusVersion current = lockStatus(voucherHeaderId);
        if (!"REOPENED".equals(current.status()) && !"REJECTED".equals(current.status())) {
            throw new IllegalStateException("Only REOPENED or REJECTED vouchers can be edited.");
        }
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
              AND version_number = :versionNumber
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
                .addValue("versionNumber", current.versionNumber())
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

    public PageResponse<VoucherSummaryDto> authorisationQueue(VoucherAuthorisationCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereSql = authorisationWhere(criteria, params);
        int page = criteria.page() == null || criteria.page() < 0 ? 0 : criteria.page();
        int size = criteria.size() == null || criteria.size() < 1 ? 25 : Math.min(criteria.size(), 250);
        params.addValue("size", size);
        params.addValue("offset", (long) page * size);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM voucher_headers h " + whereSql, params, Long.class);
        List<VoucherSummaryDto> content = jdbcTemplate.query(
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
                h.status,
                h.version_number,
                h.submitted_by,
                h.submitted_at,
                COUNT(d.voucher_detail_id)::int AS detail_count
            FROM voucher_headers h
            LEFT JOIN voucher_details d ON d.voucher_header_id = h.voucher_header_id
            """
                + whereSql
                + """
                GROUP BY h.voucher_header_id
                ORDER BY
                """
                + queueOrderBy(criteria)
                + """
                LIMIT :size
                OFFSET :offset
                """,
            params,
            SUMMARY_ROW_MAPPER
        );
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages, page == 0, totalPages == 0 || page >= totalPages - 1, content.size());
    }

    public VoucherReviewDto review(Long voucherHeaderId) {
        VoucherDto header = find(voucherHeaderId);
        Totals totals = totals(voucherHeaderId);
        List<VoucherHistoryDto> history = jdbcTemplate.query(
            """
            SELECT voucher_header_history_id, voucher_header_id, version_number, previous_status, changed_by, changed_at, change_reason
            FROM voucher_header_history
            WHERE voucher_header_id = :voucherHeaderId
            ORDER BY version_number DESC, voucher_header_history_id DESC
            """,
            new MapSqlParameterSource("voucherHeaderId", voucherHeaderId),
            HISTORY_ROW_MAPPER
        );
        MapSqlParameterSource params = new MapSqlParameterSource("voucherHeaderId", voucherHeaderId);
        return jdbcTemplate.queryForObject(
            """
            SELECT
                created_by,
                submitted_by,
                submitted_at,
                authorised_by,
                authorised_at,
                reopened_by,
                reopened_at,
                rejected_by,
                rejected_at,
                rejection_reason,
                cancelled_by,
                cancelled_at,
                cancellation_reason
            FROM voucher_headers
            WHERE voucher_header_id = :voucherHeaderId
            """,
            params,
            (rs, rowNum) -> new VoucherReviewDto(
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
                header.remarks(),
                header.status(),
                header.versionNumber(),
                rs.getObject("created_by", Long.class),
                rs.getObject("submitted_by", Long.class),
                rs.getObject("submitted_at", LocalDateTime.class),
                rs.getObject("authorised_by", Long.class),
                rs.getObject("authorised_at", LocalDateTime.class),
                rs.getObject("reopened_by", Long.class),
                rs.getObject("reopened_at", LocalDateTime.class),
                rs.getObject("rejected_by", Long.class),
                rs.getObject("rejected_at", LocalDateTime.class),
                rs.getString("rejection_reason"),
                rs.getObject("cancelled_by", Long.class),
                rs.getObject("cancelled_at", LocalDateTime.class),
                rs.getString("cancellation_reason"),
                totals.debitTotal(),
                totals.creditTotal(),
                header.details(),
                history
            )
        );
    }

    public VoucherDto authorise(Long voucherHeaderId, Long userId) {
        StatusVersion current = lockStatus(voucherHeaderId);
        requireStatus(current, "SUBMITTED");
        int updated = jdbcTemplate.update(
            """
            UPDATE voucher_headers
            SET
                status = 'AUTHORISED',
                authorised_by = :userId,
                authorised_at = CURRENT_TIMESTAMP,
                rejection_reason = NULL,
                rejected_by = NULL,
                rejected_at = NULL,
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE voucher_header_id = :voucherHeaderId
              AND status = 'SUBMITTED'
              AND version_number = :versionNumber
            """,
            statusParams(voucherHeaderId, userId, current)
        );
        requireUpdated(updated);
        audit(voucherHeaderId, "AUTHORISED", null, snapshot(voucherHeaderId), userId);
        return find(voucherHeaderId);
    }

    public VoucherDto reject(Long voucherHeaderId, Long userId, String reason) {
        StatusVersion current = lockStatus(voucherHeaderId);
        requireStatus(current, "SUBMITTED");
        int updated = jdbcTemplate.update(
            """
            UPDATE voucher_headers
            SET
                status = 'REJECTED',
                rejected_by = :userId,
                rejected_at = CURRENT_TIMESTAMP,
                rejection_reason = :reason,
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE voucher_header_id = :voucherHeaderId
              AND status = 'SUBMITTED'
              AND version_number = :versionNumber
            """,
            statusParams(voucherHeaderId, userId, current).addValue("reason", clean(reason))
        );
        requireUpdated(updated);
        audit(voucherHeaderId, "REJECTED", null, snapshot(voucherHeaderId), userId);
        return find(voucherHeaderId);
    }

    public VoucherDto cancel(Long voucherHeaderId, Long userId, String reason) {
        StatusVersion current = lockStatus(voucherHeaderId);
        if (!List.of("SUBMITTED", "REJECTED", "REOPENED").contains(current.status())) {
            throw new IllegalStateException("Only SUBMITTED, REJECTED, or REOPENED vouchers can be cancelled.");
        }
        int updated = jdbcTemplate.update(
            """
            UPDATE voucher_headers
            SET
                status = 'CANCELLED',
                cancelled_by = :userId,
                cancelled_at = CURRENT_TIMESTAMP,
                cancellation_reason = :reason,
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE voucher_header_id = :voucherHeaderId
              AND status IN ('SUBMITTED', 'REJECTED', 'REOPENED')
              AND version_number = :versionNumber
            """,
            statusParams(voucherHeaderId, userId, current).addValue("reason", clean(reason))
        );
        requireUpdated(updated);
        audit(voucherHeaderId, "CANCELLED", null, snapshot(voucherHeaderId), userId);
        return find(voucherHeaderId);
    }

    public VoucherDto reopen(Long voucherHeaderId, Long userId, String reason) {
        StatusVersion current = lockStatus(voucherHeaderId);
        requireStatus(current, "AUTHORISED");
        Long historyId = createHistorySnapshot(voucherHeaderId, current, userId, clean(reason) == null ? "Voucher reopened for correction" : clean(reason));
        jdbcTemplate.update(
            """
            INSERT INTO voucher_detail_history (
                voucher_header_history_id,
                voucher_header_id,
                voucher_detail_id,
                version_number,
                serial_number,
                detail_snapshot
            )
            SELECT
                :historyId,
                voucher_header_id,
                voucher_detail_id,
                :versionNumber,
                serial_number,
                to_jsonb(voucher_details)
            FROM voucher_details
            WHERE voucher_header_id = :voucherHeaderId
            ORDER BY serial_number
            """,
            new MapSqlParameterSource()
                .addValue("historyId", historyId)
                .addValue("voucherHeaderId", voucherHeaderId)
                .addValue("versionNumber", current.versionNumber())
        );
        int updated = jdbcTemplate.update(
            """
            UPDATE voucher_headers
            SET
                status = 'REOPENED',
                reopened_by = :userId,
                reopened_at = CURRENT_TIMESTAMP,
                version_number = version_number + 1,
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE voucher_header_id = :voucherHeaderId
              AND status = 'AUTHORISED'
              AND version_number = :versionNumber
            """,
            statusParams(voucherHeaderId, userId, current)
        );
        requireUpdated(updated);
        audit(voucherHeaderId, "REOPENED", null, snapshot(voucherHeaderId), userId);
        return find(voucherHeaderId);
    }

    public VoucherDto resubmit(Long voucherHeaderId, Long userId) {
        StatusVersion current = lockStatus(voucherHeaderId);
        if (!List.of("REOPENED", "REJECTED").contains(current.status())) {
            throw new IllegalStateException("Only REOPENED or REJECTED vouchers can be resubmitted.");
        }
        int updated = jdbcTemplate.update(
            """
            UPDATE voucher_headers
            SET
                status = 'SUBMITTED',
                submitted_by = :userId,
                submitted_at = CURRENT_TIMESTAMP,
                rejected_by = NULL,
                rejected_at = NULL,
                rejection_reason = NULL,
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE voucher_header_id = :voucherHeaderId
              AND status IN ('REOPENED', 'REJECTED')
              AND version_number = :versionNumber
            """,
            statusParams(voucherHeaderId, userId, current)
        );
        requireUpdated(updated);
        audit(voucherHeaderId, "RESUBMITTED", null, snapshot(voucherHeaderId), userId);
        return find(voucherHeaderId);
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

    private String authorisationWhere(VoucherAuthorisationCriteria criteria, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder("WHERE h.status = 'SUBMITTED'\n");
        String keyword = pattern(criteria.keyword());
        if (keyword != null) {
            sql.append("""
                AND (
                    h.voucher_number ILIKE :keyword
                    OR h.contract_number ILIKE :keyword
                    OR h.header_control_name ILIKE :keyword
                    OR h.header_control_code ILIKE :keyword
                    OR EXISTS (
                        SELECT 1
                        FROM voucher_details vd_keyword
                        WHERE vd_keyword.voucher_header_id = h.voucher_header_id
                          AND (
                              vd_keyword.party_name ILIKE :keyword
                              OR vd_keyword.loan_reference ILIKE :keyword
                              OR vd_keyword.ledger_name ILIKE :keyword
                          )
                    )
                )
                """);
            params.addValue("keyword", keyword);
        }
        String voucherType = clean(criteria.voucherType());
        if (voucherType != null && !"ALL".equalsIgnoreCase(voucherType)) {
            sql.append("AND UPPER(h.voucher_type) = UPPER(:voucherType)\n");
            params.addValue("voucherType", voucherType);
        }
        if (criteria.voucherDateFrom() != null) {
            sql.append("AND h.voucher_date >= :voucherDateFrom\n");
            params.addValue("voucherDateFrom", criteria.voucherDateFrom());
        }
        if (criteria.voucherDateTo() != null) {
            sql.append("AND h.voucher_date <= :voucherDateTo\n");
            params.addValue("voucherDateTo", criteria.voucherDateTo());
        }
        if (criteria.minimumAmount() != null) {
            sql.append("AND h.voucher_amount >= :minimumAmount\n");
            params.addValue("minimumAmount", criteria.minimumAmount());
        }
        if (criteria.maximumAmount() != null) {
            sql.append("AND h.voucher_amount <= :maximumAmount\n");
            params.addValue("maximumAmount", criteria.maximumAmount());
        }
        if (criteria.submittedBy() != null) {
            sql.append("AND h.submitted_by = :submittedBy\n");
            params.addValue("submittedBy", criteria.submittedBy());
        }
        return sql.toString();
    }

    private String queueOrderBy(VoucherAuthorisationCriteria criteria) {
        String direction = "ASC".equalsIgnoreCase(criteria.sortDirection()) ? "ASC" : "DESC";
        String column = switch (clean(criteria.sortColumn()) == null ? "" : clean(criteria.sortColumn()).toLowerCase(Locale.ROOT)) {
            case "vouchertype" -> "h.voucher_type";
            case "vouchernumber" -> "h.voucher_number";
            case "voucherdate" -> "h.voucher_date";
            case "voucheramount" -> "h.voucher_amount";
            case "submittedby" -> "h.submitted_by";
            case "submittedat" -> "h.submitted_at";
            default -> "h.submitted_at";
        };
        return column + " " + direction + " NULLS LAST, h.voucher_header_id DESC\n";
    }

    private StatusVersion lockStatus(Long voucherHeaderId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT status, version_number
            FROM voucher_headers
            WHERE voucher_header_id = :voucherHeaderId
            FOR UPDATE
            """,
            new MapSqlParameterSource("voucherHeaderId", voucherHeaderId),
            (rs, rowNum) -> new StatusVersion(rs.getString("status"), rs.getObject("version_number", Integer.class))
        );
    }

    private Long createHistorySnapshot(Long voucherHeaderId, StatusVersion current, Long userId, String reason) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO voucher_header_history (
                voucher_header_id,
                version_number,
                previous_status,
                changed_by,
                change_reason,
                header_snapshot
            )
            SELECT
                voucher_header_id,
                :versionNumber,
                :previousStatus,
                :userId,
                :reason,
                to_jsonb(voucher_headers)
            FROM voucher_headers
            WHERE voucher_header_id = :voucherHeaderId
            RETURNING voucher_header_history_id
            """,
            new MapSqlParameterSource()
                .addValue("voucherHeaderId", voucherHeaderId)
                .addValue("versionNumber", current.versionNumber())
                .addValue("previousStatus", current.status())
                .addValue("userId", userId)
                .addValue("reason", reason),
            Long.class
        );
    }

    private Totals totals(Long voucherHeaderId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                COALESCE(SUM(debit_amount), 0) AS debit_total,
                COALESCE(SUM(credit_amount), 0) AS credit_total
            FROM voucher_details
            WHERE voucher_header_id = :voucherHeaderId
            """,
            new MapSqlParameterSource("voucherHeaderId", voucherHeaderId),
            (rs, rowNum) -> new Totals(rs.getBigDecimal("debit_total"), rs.getBigDecimal("credit_total"))
        );
    }

    private MapSqlParameterSource statusParams(Long voucherHeaderId, Long userId, StatusVersion current) {
        return new MapSqlParameterSource()
            .addValue("voucherHeaderId", voucherHeaderId)
            .addValue("userId", userId)
            .addValue("versionNumber", current.versionNumber());
    }

    private void requireStatus(StatusVersion current, String expectedStatus) {
        if (!expectedStatus.equals(current.status())) {
            throw new IllegalStateException("Voucher status changed. Expected " + expectedStatus + " but found " + current.status() + ".");
        }
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("Voucher status changed before the action could be completed.");
        }
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

    private record StatusVersion(String status, Integer versionNumber) {
    }

    private record Totals(BigDecimal debitTotal, BigDecimal creditTotal) {
    }
}
