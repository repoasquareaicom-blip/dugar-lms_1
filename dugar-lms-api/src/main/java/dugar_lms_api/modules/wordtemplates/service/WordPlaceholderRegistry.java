package dugar_lms_api.modules.wordtemplates.service;

import dugar_lms_api.modules.wordtemplates.dto.WordPlaceholderFieldDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WordPlaceholderRegistry {

    public static final String CONTRACT = "CONTRACT";

    private static final Set<String> BLOCKED_COLUMNS = Set.of(
        "id", "contract_id", "contract_detail_id", "contract_financial_term_id", "repayment_structure_id",
        "pending_document_id", "asset_id", "asset_insurance_id", "created_at", "created_by", "updated_at",
        "updated_by", "is_active", "source_row_hash", "source_table", "password", "password_hash", "secret",
        "token", "checksum"
    );

    private static final List<SourceSpec> CONTRACT_SOURCES = List.of(
        new SourceSpec("Contract", "CONTRACT", "contracts", "", false),
        new SourceSpec("Borrower", "BORROWER", "party_masters", "borrower", false),
        new SourceSpec("Co Applicant", "CO_APPLICANT", "party_masters", "coApplicant", false),
        new SourceSpec("Guarantor 1", "GUARANTOR_1", "party_masters", "guarantor1", false),
        new SourceSpec("Guarantor 2", "GUARANTOR_2", "party_masters", "guarantor2", false),
        new SourceSpec("Address", "BORROWER", "party_masters", "borrower", false),
        new SourceSpec("Branch", "BRANCH", "area_masters", "branch", false),
        new SourceSpec("Loan Details", "DETAILS", "contract_details", "details", false),
        new SourceSpec("Financial Terms", "FINANCIAL", "contract_financial_terms", "financial", false),
        new SourceSpec("Vehicle / Asset", "ASSET", "assets", "asset1", false),
        new SourceSpec("Insurance", "INSURANCE", "asset_insurances", "insurance1", false),
        new SourceSpec("Repayment Structure", "REPAYMENT_1", "contract_repayment_structures", "repayment1", true),
        new SourceSpec("Repayment Structure", "REPAYMENT_2", "contract_repayment_structures", "repayment2", true),
        new SourceSpec("Repayment Structure", "REPAYMENT_3", "contract_repayment_structures", "repayment3", true),
        new SourceSpec("Pending Documents", "PENDING_DOCUMENT_1", "contract_pending_documents", "pendingDocument1", true),
        new SourceSpec("Pending Documents", "PENDING_DOCUMENT_2", "contract_pending_documents", "pendingDocument2", true),
        new SourceSpec("Pending Documents", "PENDING_DOCUMENT_3", "contract_pending_documents", "pendingDocument3", true)
    );

    private static final Map<String, String> CATEGORY_OVERRIDES = Map.ofEntries(
        Map.entry("borrower:address_line_1", "Address"),
        Map.entry("borrower:address_line_2", "Address"),
        Map.entry("borrower:area", "Address"),
        Map.entry("borrower:city", "Address"),
        Map.entry("borrower:state", "Address"),
        Map.entry("borrower:pin_code", "Address")
    );

    private static final List<WordPlaceholderFieldDto> CALCULATED_FIELDS = List.of(
        calculated("Loan Details", "Loan Amount in Words", "{{LOAN_AMOUNT_IN_WORDS}}"),
        calculated("Contract", "Formatted Contract Date", "{{FORMATTED_CONTRACT_DATE}}"),
        calculated("Loan Details", "Loan Tenure", "{{LOAN_TENURE}}"),
        calculated("Loan Details", "EMI Amount", "{{EMI_AMOUNT}}"),
        calculated("Repayment Structure", "Repayment Summary", "{{REPAYMENT_SUMMARY}}")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WordPlaceholderRegistry(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WordPlaceholderFieldDto> fields(String dataSource) {
        if (!CONTRACT.equalsIgnoreCase(String.valueOf(dataSource == null ? CONTRACT : dataSource))) {
            return List.of();
        }
        Map<String, List<ColumnInfo>> columnsByTable = columnsByTable(CONTRACT_SOURCES.stream().map(SourceSpec::tableName).distinct().toList());
        List<WordPlaceholderFieldDto> fields = new ArrayList<>();
        for (SourceSpec source : CONTRACT_SOURCES) {
            for (ColumnInfo column : columnsByTable.getOrDefault(source.tableName(), List.of())) {
                if (isBlocked(column.columnName())) {
                    continue;
                }
                String code = "{{" + source.prefix() + "_" + normalizeCode(column.columnName()) + "}}";
                fields.add(new WordPlaceholderFieldDto(
                    CONTRACT,
                    CATEGORY_OVERRIDES.getOrDefault(source.roleKey() + ":" + column.columnName(), source.category()),
                    label(column.columnName()),
                    code,
                    source.tableName(),
                    column.columnName(),
                    false,
                    source.repeating()
                ));
            }
        }
        fields.addAll(CALCULATED_FIELDS);
        return fields.stream()
            .collect(Collectors.toMap(WordPlaceholderFieldDto::placeholderCode, field -> field, (left, right) -> left))
            .values()
            .stream()
            .sorted(Comparator.comparing(WordPlaceholderFieldDto::category).thenComparing(WordPlaceholderFieldDto::placeholderCode))
            .toList();
    }

    public Set<String> knownCodes(String dataSource) {
        return fields(dataSource).stream().map(WordPlaceholderFieldDto::placeholderCode).collect(Collectors.toSet());
    }

    private Map<String, List<ColumnInfo>> columnsByTable(List<String> tables) {
        return jdbcTemplate.query(
            """
            SELECT table_name, column_name, ordinal_position
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name IN (:tables)
            ORDER BY table_name, ordinal_position
            """,
            new MapSqlParameterSource("tables", tables),
            (rs, rowNum) -> new TableColumn(
                rs.getString("table_name"),
                new ColumnInfo(rs.getString("column_name"), rs.getInt("ordinal_position"))
            )
        ).stream().collect(Collectors.groupingBy(TableColumn::tableName, Collectors.mapping(TableColumn::column, Collectors.toList())));
    }

    private boolean isBlocked(String columnName) {
        String normalized = columnName.toLowerCase(Locale.ROOT);
        return BLOCKED_COLUMNS.contains(normalized)
            || normalized.endsWith("_id")
            || normalized.contains("hash")
            || normalized.contains("secret")
            || normalized.contains("password")
            || normalized.contains("token");
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    private String label(String value) {
        String[] parts = value.split("_+");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT));
        }
        return String.join(" ", words);
    }

    private static WordPlaceholderFieldDto calculated(String category, String fieldName, String code) {
        return new WordPlaceholderFieldDto(CONTRACT, category, fieldName, code, "calculated", null, true, false);
    }

    private record SourceSpec(String category, String prefix, String tableName, String roleKey, boolean repeating) {
    }

    private record ColumnInfo(String columnName, int ordinalPosition) {
    }

    private record TableColumn(String tableName, ColumnInfo column) {
    }
}
