package dugar_lms_api.modules.wordtemplates.repository;

import dugar_lms_api.modules.reports.ReportAccessScope;
import dugar_lms_api.modules.wordtemplates.dto.WordContractOptionDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class WordDocumentGenerationRepository {

    private static final String ACCESS_FILTER = """
        (
            :fullAccess = TRUE
            OR (
                EXISTS (
                    SELECT 1
                    FROM users access_user
                    WHERE access_user.user_id::text = TRIM(COALESCE(c.updated_by, ''))
                      AND LOWER(TRIM(COALESCE(access_user.user_group, ''))) = 'user'
                )
                AND (
                    EXISTS (SELECT 1 FROM users scope_user WHERE scope_user.user_id = :scopeUserId AND UPPER(TRIM(COALESCE(scope_user.user_type, 'USER'))) = 'USER')
                    OR EXISTS (
                        SELECT 1
                        FROM user_areas scope_area
                        JOIN users scope_user ON scope_user.user_id = scope_area.user_id
                        WHERE scope_area.user_id = :scopeUserId
                          AND UPPER(TRIM(COALESCE(scope_user.user_type, ''))) IN ('BRANCH', 'STATE')
                          AND UPPER(TRIM(scope_area.area_code)) = UPPER(TRIM(COALESCE(c.area_code, '')))
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_contracts scope_contract
                        JOIN users scope_user ON scope_user.user_id = scope_contract.user_id
                        WHERE scope_contract.user_id = :scopeUserId
                          AND UPPER(TRIM(COALESCE(scope_user.user_type, ''))) = 'CUSTOMER'
                          AND UPPER(TRIM(scope_contract.contract_number)) = UPPER(TRIM(COALESCE(c.contract_number, '')))
                    )
                )
            )
        )
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WordDocumentGenerationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WordContractOptionDto> searchContracts(String keyword, int limit, ReportAccessScope scope) {
        String cleanedKeyword = keyword == null || keyword.isBlank() ? "" : "%" + keyword.trim().toLowerCase() + "%";
        return jdbcTemplate.query(
            """
            SELECT
                c.contract_id,
                c.contract_number,
                c.borrower_code,
                COALESCE(NULLIF(TRIM(pm.full_name), ''), NULLIF(TRIM(c.borrower_code), ''), '-') AS borrower_name
            FROM contracts c
            LEFT JOIN party_masters pm
              ON UPPER(TRIM(pm.party_code)) = UPPER(TRIM(c.borrower_code))
             AND pm.is_active = TRUE
            WHERE c.is_active = TRUE
              AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
              AND """ + ACCESS_FILTER + """
              AND (
                  :keyword = ''
                  OR LOWER(TRIM(COALESCE(c.contract_number, ''))) LIKE :keyword
                  OR LOWER(TRIM(COALESCE(pm.full_name, ''))) LIKE :keyword
                  OR LOWER(TRIM(COALESCE(c.borrower_code, ''))) LIKE :keyword
              )
            ORDER BY c.contract_number
            LIMIT :limit
            """,
            accessParams(scope)
                .addValue("keyword", cleanedKeyword)
                .addValue("limit", Math.max(1, Math.min(limit, 50))),
            (rs, rowNum) -> {
                String contractNumber = rs.getString("contract_number");
                String borrowerName = rs.getString("borrower_name");
                String borrowerCode = rs.getString("borrower_code");
                return new WordContractOptionDto(
                    rs.getLong("contract_id"),
                    contractNumber,
                    borrowerName,
                    borrowerCode,
                    contractNumber + " - " + borrowerName
                );
            }
        );
    }

    public Optional<Map<String, Object>> findAccessibleContract(Long contractId, ReportAccessScope scope) {
        return jdbcTemplate.queryForList(
            """
            SELECT *
            FROM contracts c
            WHERE c.contract_id = :contractId
              AND """ + ACCESS_FILTER + """
            """,
            accessParams(scope).addValue("contractId", contractId)
        ).stream().findFirst();
    }

    public Optional<Map<String, Object>> findActiveByContractId(String table, Long contractId, String orderColumn) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM " + table + " WHERE contract_id = :contractId AND COALESCE(is_active, TRUE) = TRUE ORDER BY " + orderColumn + " LIMIT 1",
            new MapSqlParameterSource("contractId", contractId)
        ).stream().findFirst();
    }

    public List<Map<String, Object>> findRowsByContractId(String table, Long contractId, String orderColumn, int limit) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM " + table + " WHERE contract_id = :contractId ORDER BY " + orderColumn + " LIMIT :limit",
            new MapSqlParameterSource()
                .addValue("contractId", contractId)
                .addValue("limit", limit)
        );
    }

    public Optional<Map<String, Object>> findParty(String partyCode, String partyType) {
        if (partyCode == null || partyCode.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.queryForList(
            """
            SELECT *
            FROM party_masters
            WHERE UPPER(TRIM(party_code)) = UPPER(TRIM(:partyCode))
              AND (:partyType IS NULL OR UPPER(TRIM(party_type)) = :partyType)
              AND is_active = TRUE
            ORDER BY id
            LIMIT 1
            """,
            new MapSqlParameterSource()
                .addValue("partyCode", partyCode)
                .addValue("partyType", partyType)
        ).stream().findFirst();
    }

    public Optional<Map<String, Object>> findBranch(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.queryForList(
            """
            SELECT *
            FROM area_masters
            WHERE UPPER(TRIM(area_code)) = UPPER(TRIM(:areaCode))
              AND is_active = TRUE
            ORDER BY area_code
            LIMIT 1
            """,
            new MapSqlParameterSource("areaCode", areaCode)
        ).stream().findFirst();
    }

    public Optional<Map<String, Object>> findInsurance(Long assetId) {
        if (assetId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.queryForList(
            """
            SELECT *
            FROM asset_insurances
            WHERE asset_id = :assetId
            ORDER BY asset_insurance_id
            LIMIT 1
            """,
            new MapSqlParameterSource("assetId", assetId)
        ).stream().findFirst();
    }

    public Optional<String> findOtherDescription(String otherCode) {
        if (otherCode == null || otherCode.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.queryForList(
            """
            SELECT MAX(NULLIF(TRIM(other_desc), '')) AS other_desc
            FROM g_others
            WHERE UPPER(TRIM(other_code)) = UPPER(TRIM(:otherCode))
            """,
            new MapSqlParameterSource("otherCode", otherCode),
            String.class
        ).stream().filter(value -> value != null && !value.isBlank()).findFirst();
    }

    private MapSqlParameterSource accessParams(ReportAccessScope scope) {
        return new MapSqlParameterSource()
            .addValue("fullAccess", scope == null || scope.fullAccess())
            .addValue("scopeUserId", scope == null ? null : scope.userId());
    }
}
