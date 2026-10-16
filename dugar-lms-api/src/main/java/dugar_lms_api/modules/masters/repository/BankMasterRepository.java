package dugar_lms_api.modules.masters.repository;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.masters.dto.BankMasterDto;
import dugar_lms_api.modules.masters.dto.BankMasterRequest;
import dugar_lms_api.modules.masters.service.BankMasterCriteria;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;

@Repository
public class BankMasterRepository {

    private static final String BASE_SELECT = """
        SELECT
            id,
            bank_code,
            bank_name,
            is_active,
            created_at,
            updated_at
        FROM bank_masters
        """;

    private static final RowMapper<BankMasterDto> ROW_MAPPER = (rs, rowNum) -> new BankMasterDto(
        rs.getLong("id"),
        rs.getString("bank_code"),
        rs.getString("bank_name"),
        rs.getBoolean("is_active"),
        rs.getObject("created_at", java.time.LocalDateTime.class),
        rs.getObject("updated_at", java.time.LocalDateTime.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BankMasterRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<BankMasterDto> find(BankMasterCriteria criteria) {
        int page = criteria.page() == null || criteria.page() < 0 ? 0 : criteria.page();
        int size = criteria.size() == null || criteria.size() <= 0 ? 25 : Math.min(criteria.size(), 200);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", size)
            .addValue("offset", page * size);

        String where = where(criteria, params);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bank_masters " + where, params, Long.class);
        List<BankMasterDto> content = jdbcTemplate.query(
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

    public List<BankMasterDto> findActive() {
        return jdbcTemplate.query(
            BASE_SELECT + " WHERE is_active = TRUE ORDER BY bank_name ASC, bank_code ASC",
            ROW_MAPPER
        );
    }

    public BankMasterDto create(BankMasterRequest request) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_masters (
                bank_code,
                bank_name,
                is_active,
                created_at,
                updated_at
            )
            VALUES (
                :bankCode,
                :bankName,
                :isActive,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id, bank_code, bank_name, is_active, created_at, updated_at
            """,
            params(request, true),
            ROW_MAPPER
        );
    }

    public BankMasterDto update(Long id, BankMasterRequest request) {
        return jdbcTemplate.queryForObject(
            """
            UPDATE bank_masters
            SET
                bank_code = :bankCode,
                bank_name = :bankName,
                is_active = COALESCE(:isActive, is_active),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            RETURNING id, bank_code, bank_name, is_active, created_at, updated_at
            """,
            params(request, false).addValue("id", id),
            ROW_MAPPER
        );
    }

    private String where(BankMasterCriteria criteria, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            where.append("""
                 AND (
                    bank_code ILIKE :keyword
                    OR bank_name ILIKE :keyword
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

    private MapSqlParameterSource params(BankMasterRequest request, boolean defaultActive) {
        Boolean isActive = request.isActive();
        if (defaultActive && isActive == null) {
            isActive = true;
        }
        return new MapSqlParameterSource()
            .addValue("bankCode", cleanCode(request.bankCode()))
            .addValue("bankName", clean(request.bankName()))
            .addValue("isActive", isActive);
    }

    private String sortColumn(String sortColumn) {
        if (sortColumn == null) {
            return "bank_code";
        }
        return switch (sortColumn) {
            case "bankName" -> "bank_name";
            case "isActive" -> "is_active";
            case "updatedAt" -> "updated_at";
            default -> "bank_code";
        };
    }

    private String sortDirection(String sortDirection) {
        return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
    }

    private String cleanCode(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
