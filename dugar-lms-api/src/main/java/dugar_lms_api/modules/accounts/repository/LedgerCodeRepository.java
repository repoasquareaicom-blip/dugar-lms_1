package dugar_lms_api.modules.accounts.repository;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accounts.dto.LedgerCodeDto;
import dugar_lms_api.modules.accounts.dto.LedgerCodeRequest;
import dugar_lms_api.modules.accounts.service.LedgerCodeCriteria;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Repository
public class LedgerCodeRepository {

    private static final String BASE_SELECT = """
        SELECT
            ledger_id,
            ledger_code,
            ledger_name,
            COALESCE(active_opening_balance, 0) AS opening_balance,
            account_type,
            schedule_debit_sub_code,
            schedule_credit_sub_code,
            trial_balance_major_code,
            trial_balance_minor_code,
            pl_bs_flag,
            has_sub_ledger,
            link_reference,
            legacy_user_id,
            legacy_user_doc,
            source_system,
            is_active,
            created_by,
            created_at,
            updated_by,
            updated_at
        FROM (
            SELECT
                ledger_codes.*,
                ledger_opening_balances.account_balance AS active_opening_balance
            FROM ledger_codes
            LEFT JOIN ledger_opening_balances
              ON ledger_opening_balances.ledger_id = ledger_codes.ledger_id
             AND ledger_opening_balances.is_active = TRUE
        ) ledger_codes
        """;

    private static final RowMapper<LedgerCodeDto> ROW_MAPPER = (rs, rowNum) -> new LedgerCodeDto(
        rs.getLong("ledger_id"),
        rs.getString("ledger_code"),
        rs.getString("ledger_name"),
        rs.getBigDecimal("opening_balance"),
        rs.getString("account_type"),
        rs.getString("schedule_debit_sub_code"),
        rs.getString("schedule_credit_sub_code"),
        rs.getString("trial_balance_major_code"),
        rs.getString("trial_balance_minor_code"),
        rs.getString("pl_bs_flag"),
        rs.getBoolean("has_sub_ledger"),
        rs.getBoolean("link_reference"),
        rs.getString("legacy_user_id"),
        rs.getObject("legacy_user_doc", java.time.LocalDate.class),
        rs.getString("source_system"),
        rs.getBoolean("is_active"),
        rs.getObject("created_by", Long.class),
        rs.getObject("created_at", java.time.LocalDateTime.class),
        rs.getObject("updated_by", Long.class),
        rs.getObject("updated_at", java.time.LocalDateTime.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LedgerCodeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<LedgerCodeDto> find(LedgerCodeCriteria criteria) {
        int page = criteria.page() == null || criteria.page() < 0 ? 0 : criteria.page();
        int size = criteria.size() == null || criteria.size() <= 0 ? 25 : Math.min(criteria.size(), 200);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", size)
            .addValue("offset", page * size);

        String where = where(criteria, params);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_codes " + where, params, Long.class);
        List<LedgerCodeDto> content = jdbcTemplate.query(
            BASE_SELECT + where + " ORDER BY " + sortColumn(criteria.sortColumn()) + " " + sortDirection(criteria.sortDirection()) + " LIMIT :limit OFFSET :offset",
            params,
            ROW_MAPPER
        );
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(
            content,
            page,
            size,
            total,
            totalPages,
            page == 0,
            page >= Math.max(totalPages - 1, 0),
            content.size()
        );
    }

    public LedgerCodeDto create(LedgerCodeRequest request, Long userId) {
        LedgerCodeDto ledger = jdbcTemplate.queryForObject(
            """
            INSERT INTO ledger_codes (
                ledger_code,
                ledger_name,
                source_system,
                is_active,
                created_by,
                updated_by,
                updated_at
            )
            VALUES (
                :ledgerCode,
                :ledgerName,
                'LMS',
                :isActive,
                :userId,
                :userId,
                CURRENT_TIMESTAMP
            )
            RETURNING *, 0::numeric AS opening_balance
            """,
            params(request, true).addValue("userId", userId),
            ROW_MAPPER
        );
        saveOpeningBalance(ledger.ledgerId(), ledger.ledgerCode(), balance(request.openingBalance()), userId);
        return findByLedgerCode(ledger.ledgerCode());
    }

    public LedgerCodeDto update(String ledgerCode, LedgerCodeRequest request, Long userId) {
        LedgerCodeDto ledger = jdbcTemplate.queryForObject(
            """
            UPDATE ledger_codes
            SET
                ledger_code = :ledgerCode,
                ledger_name = :ledgerName,
                is_active = COALESCE(:isActive, is_active),
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE UPPER(ledger_code) = UPPER(:pathLedgerCode)
            RETURNING *, 0::numeric AS opening_balance
            """,
            params(request, false)
                .addValue("pathLedgerCode", ledgerCode)
                .addValue("userId", userId),
            ROW_MAPPER
        );
        saveOpeningBalance(ledger.ledgerId(), ledger.ledgerCode(), balance(request.openingBalance()), userId);
        return findByLedgerCode(ledger.ledgerCode());
    }

    public LedgerCodeDto updateActive(String ledgerCode, boolean active, Long userId) {
        jdbcTemplate.update(
            """
            UPDATE ledger_codes
            SET
                is_active = :active,
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE UPPER(ledger_code) = UPPER(:ledgerCode)
            """,
            new MapSqlParameterSource()
                .addValue("ledgerCode", ledgerCode)
                .addValue("active", active)
                .addValue("userId", userId)
        );
        return findByLedgerCode(ledgerCode);
    }

    private String where(LedgerCodeCriteria criteria, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            where.append("""
                 AND (
                    ledger_code ILIKE :keyword
                    OR ledger_name ILIKE :keyword
                 )
                """);
            params.addValue("keyword", "%" + criteria.keyword().trim() + "%");
        }
        if (criteria.isActive() != null) {
            where.append(" AND is_active = :isActive");
            params.addValue("isActive", criteria.isActive());
        }
        return where.toString();
    }

    private MapSqlParameterSource params(LedgerCodeRequest request, boolean defaultActive) {
        Boolean isActive = request.isActive();
        if (defaultActive && isActive == null) {
            isActive = true;
        }
        return new MapSqlParameterSource()
            .addValue("ledgerCode", clean(request.ledgerCode()))
            .addValue("ledgerName", clean(request.ledgerName()))
            .addValue("isActive", isActive);
    }

    private LedgerCodeDto findByLedgerCode(String ledgerCode) {
        return jdbcTemplate.queryForObject(
            BASE_SELECT + " WHERE UPPER(ledger_code) = UPPER(:ledgerCode)",
            new MapSqlParameterSource("ledgerCode", ledgerCode),
            ROW_MAPPER
        );
    }

    private void saveOpeningBalance(Long ledgerId, String ledgerCode, BigDecimal openingBalance, Long userId) {
        jdbcTemplate.update(
            """
            UPDATE ledger_opening_balances
            SET
                is_active = FALSE,
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE (ledger_id = :ledgerId OR UPPER(ledger_code) = UPPER(:ledgerCode))
              AND is_active = TRUE
            """,
            new MapSqlParameterSource()
                .addValue("ledgerId", ledgerId)
                .addValue("ledgerCode", ledgerCode)
                .addValue("userId", userId)
        );

        jdbcTemplate.update(
            """
            INSERT INTO ledger_opening_balances (
                ledger_id,
                ledger_code,
                account_balance,
                entered_date,
                is_active,
                created_by,
                updated_by,
                updated_at
            )
            VALUES (
                :ledgerId,
                :ledgerCode,
                :openingBalance,
                CURRENT_DATE,
                TRUE,
                :userId,
                :userId,
                CURRENT_TIMESTAMP
            )
            """,
            new MapSqlParameterSource()
                .addValue("ledgerId", ledgerId)
                .addValue("ledgerCode", ledgerCode)
                .addValue("openingBalance", openingBalance)
                .addValue("userId", userId)
        );
    }

    private BigDecimal balance(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String sortColumn(String sortColumn) {
        if (sortColumn == null) {
            return "ledger_code";
        }
        return switch (sortColumn) {
            case "ledgerName" -> "ledger_name";
            case "accountType" -> "account_type";
            case "plBsFlag" -> "pl_bs_flag";
            case "isActive" -> "is_active";
            case "updatedAt" -> "updated_at";
            default -> "ledger_code";
        };
    }

    private String sortDirection(String sortDirection) {
        return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned.toUpperCase(Locale.ROOT);
    }
}
