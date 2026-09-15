package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.accessmanagement.dto.ContractOptionDto;
import dugar_lms_api.modules.contracts.dto.ContractAreaOptionDto;
import dugar_lms_api.modules.contracts.dto.ContractListDto;
import dugar_lms_api.modules.contracts.service.ContractListCriteria;
import dugar_lms_api.modules.contracts.service.ContractListSortDirection;
import dugar_lms_api.modules.contracts.service.ContractListSortField;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ContractListRepository {

    private static final String CONTRACT_LIST_COLUMNS = """
        c.contract_id,
        c.contract_number,
        COALESCE(flags.flag_count, 0) AS flag_count,
        COALESCE(NULLIF(TRIM(c.legacy_contract_number), ''), '-') AS legacy_contract_number,
        COALESCE(NULLIF(TRIM(c.contract_type), ''), '-') AS product,
        COALESCE(NULLIF(TRIM(c.area_code), ''), '-') AS branch,
        COALESCE(NULLIF(TRIM(c.status), ''), '-') AS status,
        COALESCE(NULLIF(TRIM(c.category), ''), '-') AS category,
        COALESCE(NULLIF(TRIM(c.risk_level), ''), '-') AS risk_level,
        c.contract_date,
        c.loan_amount,
        c.total_contract_value,
        c.insurance_deposit,
        c.finance_charges,
        c.bpfc_amount,
        c.prompt_payment_rebate,
        c.tenure_months,
        c.irr_rate,
        c.first_emi_date,
        COALESCE(rs.repayment_schedule, '') AS repayment_schedule,
        COALESCE(NULLIF(TRIM(a.registration_number), ''), NULLIF(TRIM(c.registration_number), ''), '-') AS vehicle_registration_number,
        COALESCE(NULLIF(TRIM(c.vehicle_make), ''), '-') AS vehicle_make,
        COALESCE(NULLIF(TRIM(a.vehicle_type_code), ''), '-') AS vehicle_type_code,
        COALESCE(NULLIF(TRIM(a.engine_number), ''), NULLIF(TRIM(cd.engine_number), ''), '-') AS engine_number,
        COALESCE(NULLIF(TRIM(a.chassis_number), ''), NULLIF(TRIM(cd.chassis_number), ''), '-') AS chassis_number,
        COALESCE(NULLIF(TRIM(a.manufacture_year), ''), '-') AS manufacture_year,
        COALESCE(NULLIF(TRIM(a.finance_type), ''), '-') AS vehicle_finance_type,
        a.equipment_value AS vehicle_value,
        COALESCE(NULLIF(TRIM(a.security_offered), ''), '-') AS vehicle_security_offered,
        COALESCE(NULLIF(TRIM(a.owner_serial_no), ''), NULLIF(TRIM(cd.owner_serial_number), ''), '-') AS vehicle_owner_serial_no,
        cd.registration_date AS vehicle_registration_date,
        COALESCE(NULLIF(TRIM(c.borrower_code), ''), '-') AS customer_code,
        COALESCE(NULLIF(TRIM(pm.full_name), ''), NULLIF(TRIM(c.borrower_code), ''), '-') AS customer_name,
        COALESCE(NULLIF(TRIM(pm.contact_number), ''), '-') AS customer_mobile,
        COALESCE(NULLIF(TRIM(pm.email_id), ''), '-') AS customer_email,
        COALESCE(NULLIF(TRIM(CONCAT_WS(', ', NULLIF(TRIM(pm.address_line_1), ''), NULLIF(TRIM(pm.address_line_2), ''), NULLIF(TRIM(pm.area), ''))), ''), '-') AS customer_address,
        COALESCE(NULLIF(TRIM(pm.city), ''), '-') AS customer_city,
        COALESCE(NULLIF(TRIM(pm.state), ''), '-') AS customer_state,
        COALESCE(NULLIF(TRIM(pm.pin_code), ''), '-') AS customer_pin_code,
        COALESCE(NULLIF(TRIM(pm.pan_number), ''), '-') AS customer_pan_number,
        COALESCE(NULLIF(TRIM(pm.occupation), ''), '-') AS customer_occupation,
        COALESCE(NULLIF(TRIM(c.guarantor_code), ''), '-') AS guarantor_code,
        COALESCE(NULLIF(TRIM(gm.full_name), ''), NULLIF(TRIM(c.guarantor_code), ''), '-') AS guarantor_name,
        COALESCE(NULLIF(TRIM(gm.contact_number), ''), '-') AS guarantor_mobile,
        COALESCE(NULLIF(TRIM(gm.email_id), ''), '-') AS guarantor_email,
        COALESCE(NULLIF(TRIM(CONCAT_WS(', ', NULLIF(TRIM(gm.address_line_1), ''), NULLIF(TRIM(gm.address_line_2), ''), NULLIF(TRIM(gm.area), ''))), ''), '-') AS guarantor_address,
        COALESCE(NULLIF(TRIM(gm.city), ''), '-') AS guarantor_city,
        COALESCE(NULLIF(TRIM(gm.state), ''), '-') AS guarantor_state,
        COALESCE(NULLIF(TRIM(gm.pin_code), ''), '-') AS guarantor_pin_code,
        COALESCE(NULLIF(TRIM(gm.pan_number), ''), '-') AS guarantor_pan_number,
        COALESCE(NULLIF(TRIM(gm.occupation), ''), '-') AS guarantor_occupation
        """;

    private static final String FROM_SQL = """
        FROM contracts c
        LEFT JOIN contract_details cd
            ON cd.contract_id = c.contract_id
           AND cd.is_active = TRUE
        LEFT JOIN party_masters pm
            ON UPPER(TRIM(pm.party_code)) = UPPER(TRIM(c.borrower_code))
           AND pm.is_active = TRUE
        LEFT JOIN party_masters gm
            ON UPPER(TRIM(gm.party_code)) = UPPER(TRIM(c.guarantor_code))
           AND gm.is_active = TRUE
        LEFT JOIN LATERAL (
            SELECT
                asset.registration_number,
                asset.vehicle_type_code,
                asset.engine_number,
                asset.chassis_number,
                asset.manufacture_year,
                asset.finance_type,
                asset.equipment_value,
                asset.security_offered,
                asset.owner_serial_no
            FROM assets asset
            WHERE asset.contract_id = c.contract_id
            ORDER BY asset.asset_id
            LIMIT 1
        ) a ON TRUE
        LEFT JOIN LATERAL (
            SELECT STRING_AGG(
                repayment.sequence_no || '|' || repayment.number_of_installments || '|' || repayment.installment_amount,
                ';'
                ORDER BY repayment.sequence_no
            ) AS repayment_schedule
            FROM contract_repayment_structures repayment
            WHERE repayment.contract_id = c.contract_id
        ) rs ON TRUE
        LEFT JOIN LATERAL (
            SELECT COUNT(*)::integer AS flag_count
            FROM contract_flags contract_flag
            WHERE contract_flag.contract_id = c.contract_id
        ) flags ON TRUE
        """;

    private static final String FIND_SQL_PREFIX = """
        SELECT
        """ + CONTRACT_LIST_COLUMNS + FROM_SQL;

    private static final String COUNT_SQL = """
        SELECT COUNT(*)
        """ + FROM_SQL;

    private static final String FIND_AREAS_SQL = """
        SELECT DISTINCT
            NULLIF(TRIM(c.area_code), '') AS area_code,
            NULLIF(TRIM(am.area_name), '') AS area_name
        FROM contracts c
        LEFT JOIN area_masters am
          ON UPPER(TRIM(am.area_code)) = UPPER(TRIM(c.area_code))
        WHERE c.is_active = TRUE
          AND NULLIF(TRIM(c.area_code), '') IS NOT NULL
          AND (
              :keyword = ''
              OR LOWER(TRIM(c.area_code)) LIKE :keyword
              OR LOWER(TRIM(COALESCE(am.area_name, ''))) LIKE :keyword
          )
          AND (
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
        ORDER BY area_code
        LIMIT :limit
        """;

    private static final String FIND_AREA_MASTER_OPTIONS_SQL = """
        SELECT
            NULLIF(TRIM(area_code), '') AS area_code,
            NULLIF(TRIM(area_name), '') AS area_name
        FROM area_masters
        WHERE is_active = TRUE
          AND NULLIF(TRIM(area_code), '') IS NOT NULL
          AND (
              :keyword = ''
              OR LOWER(TRIM(area_code)) LIKE :keyword
              OR LOWER(TRIM(COALESCE(area_name, ''))) LIKE :keyword
          )
        ORDER BY area_code
        LIMIT :limit
        """;

    private static final String FIND_ACTIVE_CONTRACT_OPTIONS_SQL = """
        SELECT
            c.contract_number,
            COALESCE(NULLIF(TRIM(pm.full_name), ''), NULLIF(TRIM(c.borrower_code), ''), '-') AS borrower_name
        FROM contracts c
        LEFT JOIN party_masters pm
            ON UPPER(TRIM(pm.party_code)) = UPPER(TRIM(c.borrower_code))
           AND pm.is_active = TRUE
        WHERE c.is_active = TRUE
          AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
          AND c.loan_close_date IS NULL
          AND NULLIF(TRIM(c.contract_number), '') IS NOT NULL
          AND (
              :keyword = ''
              OR LOWER(TRIM(c.contract_number)) LIKE :keyword
              OR LOWER(TRIM(COALESCE(pm.full_name, ''))) LIKE :keyword
              OR LOWER(TRIM(COALESCE(c.borrower_code, ''))) LIKE :keyword
          )
        ORDER BY c.contract_number
        LIMIT :limit
        """;

    private static final String EXISTS_ACTIVE_CONTRACT_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM contracts
            WHERE UPPER(TRIM(contract_number)) = UPPER(TRIM(:contractNumber))
              AND is_active = TRUE
              AND UPPER(TRIM(COALESCE(status, ''))) = 'Y'
              AND loan_close_date IS NULL
        )
        """;

    private static final String EXISTS_ACTIVE_AREA_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM area_masters
            WHERE UPPER(TRIM(area_code)) = UPPER(TRIM(:areaCode))
              AND is_active = TRUE
        )
        """;

    private static final RowMapper<ContractListDto> CONTRACT_LIST_ROW_MAPPER = (rs, rowNum) -> new ContractListDto(
        rs.getLong("contract_id"),
        rs.getString("contract_number"),
        rs.getObject("flag_count", Integer.class),
        rs.getString("legacy_contract_number"),
        rs.getString("product"),
        rs.getString("branch"),
        rs.getString("status"),
        rs.getString("category"),
        rs.getString("risk_level"),
        rs.getObject("contract_date", java.time.LocalDate.class),
        rs.getBigDecimal("loan_amount"),
        rs.getBigDecimal("total_contract_value"),
        rs.getBigDecimal("insurance_deposit"),
        rs.getBigDecimal("finance_charges"),
        rs.getBigDecimal("bpfc_amount"),
        rs.getBigDecimal("prompt_payment_rebate"),
        rs.getObject("tenure_months", Integer.class),
        rs.getBigDecimal("irr_rate"),
        rs.getObject("first_emi_date", java.time.LocalDate.class),
        rs.getString("repayment_schedule"),
        rs.getString("vehicle_registration_number"),
        rs.getString("vehicle_make"),
        rs.getString("vehicle_type_code"),
        rs.getString("engine_number"),
        rs.getString("chassis_number"),
        rs.getString("manufacture_year"),
        rs.getString("vehicle_finance_type"),
        rs.getBigDecimal("vehicle_value"),
        rs.getString("vehicle_security_offered"),
        rs.getString("vehicle_owner_serial_no"),
        rs.getObject("vehicle_registration_date", java.time.LocalDate.class),
        rs.getString("customer_code"),
        rs.getString("customer_name"),
        rs.getString("customer_mobile"),
        rs.getString("customer_email"),
        rs.getString("customer_address"),
        rs.getString("customer_city"),
        rs.getString("customer_state"),
        rs.getString("customer_pin_code"),
        rs.getString("customer_pan_number"),
        rs.getString("customer_occupation"),
        rs.getString("guarantor_code"),
        rs.getString("guarantor_name"),
        rs.getString("guarantor_mobile"),
        rs.getString("guarantor_email"),
        rs.getString("guarantor_address"),
        rs.getString("guarantor_city"),
        rs.getString("guarantor_state"),
        rs.getString("guarantor_pin_code"),
        rs.getString("guarantor_pan_number"),
        rs.getString("guarantor_occupation")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ContractListRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<ContractListDto> find(ContractListCriteria criteria) {
        return find(criteria, new ReportAccessScope(false));
    }

    public List<ContractListDto> find(ContractListCriteria criteria, ReportAccessScope accessScope) {
        QueryParts queryParts = queryParts(criteria, accessScope);
        return namedParameterJdbcTemplate.query(findSql(criteria, queryParts.whereSql()), queryParts.params(), CONTRACT_LIST_ROW_MAPPER);
    }

    public long count(ContractListCriteria criteria) {
        return count(criteria, new ReportAccessScope(false));
    }

    public long count(ContractListCriteria criteria, ReportAccessScope accessScope) {
        QueryParts queryParts = queryParts(criteria, accessScope);
        Long total = namedParameterJdbcTemplate.queryForObject(COUNT_SQL + queryParts.whereSql(), queryParts.params(), Long.class);
        return total == null ? 0 : total;
    }

    public List<ContractAreaOptionDto> findAreas(String keyword, int limit) {
        return findAreas(keyword, limit, new ReportAccessScope(false));
    }

    public List<ContractAreaOptionDto> findAreas(String keyword, int limit, ReportAccessScope accessScope) {
        String cleanedKeyword = normalizeLower(keyword);
        return namedParameterJdbcTemplate.query(
            FIND_AREAS_SQL,
            new MapSqlParameterSource()
                .addValue("keyword", cleanedKeyword == null ? "" : "%" + cleanedKeyword + "%")
                .addValue("limit", Math.max(1, Math.min(limit, 50)))
                .addValue("fullAccess", accessScope == null || accessScope.fullAccess())
                .addValue("scopeUserId", accessScope == null ? null : accessScope.userId()),
            (rs, rowNum) -> new ContractAreaOptionDto(
                rs.getString("area_code"),
                rs.getString("area_name")
            )
        );
    }

    public List<ContractAreaOptionDto> findAreaMasterOptions(String keyword, int limit) {
        String cleanedKeyword = normalizeLower(keyword);
        return namedParameterJdbcTemplate.query(
            FIND_AREA_MASTER_OPTIONS_SQL,
            new MapSqlParameterSource()
                .addValue("keyword", cleanedKeyword == null ? "" : "%" + cleanedKeyword + "%")
                .addValue("limit", Math.max(1, Math.min(limit, 50))),
            (rs, rowNum) -> new ContractAreaOptionDto(
                rs.getString("area_code"),
                rs.getString("area_name")
            )
        );
    }

    public List<ContractOptionDto> findActiveContractOptions(String keyword, int limit) {
        String cleanedKeyword = normalizeLower(keyword);
        return namedParameterJdbcTemplate.query(
            FIND_ACTIVE_CONTRACT_OPTIONS_SQL,
            new MapSqlParameterSource()
                .addValue("keyword", cleanedKeyword == null ? "" : "%" + cleanedKeyword + "%")
                .addValue("limit", Math.max(1, Math.min(limit, 50))),
            (rs, rowNum) -> {
                String contractNumber = rs.getString("contract_number");
                String borrowerName = rs.getString("borrower_name");
                return new ContractOptionDto(
                    contractNumber,
                    borrowerName,
                    contractNumber + " - " + borrowerName
                );
            }
        );
    }

    public boolean existsActiveContractNumber(String contractNumber) {
        Boolean exists = namedParameterJdbcTemplate.queryForObject(
            EXISTS_ACTIVE_CONTRACT_SQL,
            new MapSqlParameterSource("contractNumber", contractNumber),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public boolean existsActiveAreaCode(String areaCode) {
        Boolean exists = namedParameterJdbcTemplate.queryForObject(
            EXISTS_ACTIVE_AREA_SQL,
            new MapSqlParameterSource("areaCode", areaCode),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    private QueryParts queryParts(ContractListCriteria criteria) {
        return queryParts(criteria, new ReportAccessScope(false));
    }

    private QueryParts queryParts(ContractListCriteria criteria, ReportAccessScope accessScope) {
        StringBuilder whereSql = new StringBuilder("""
            WHERE c.is_active = TRUE
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        appendAccessFilter(whereSql, params, accessScope);

        if (Boolean.TRUE.equals(criteria.isDraft())) {
            if (criteria.workflowStatus() == null || criteria.workflowStatus().isBlank()) {
                whereSql.append(" AND UPPER(TRIM(COALESCE(c.status, ''))) IN ('D', 'DRAFT')\n");
            }
        } else {
            whereSql.append("""
                  AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
                  AND EXISTS (
                      SELECT 1
                      FROM contract_repayment_structures repayment_exists
                      WHERE repayment_exists.contract_id = c.contract_id
                  )
                """);
        }

        appendStatusFilter(whereSql, params, "workflowStatus", criteria.workflowStatus());
        appendKeywordFilter(whereSql, params, criteria.keyword());
        appendEqualsFilter(whereSql, params, "c.area_code", "branch", criteria.branch());
        appendStatusFilter(whereSql, params, "status", criteria.status());
        appendEqualsFilter(whereSql, params, "c.contract_type", "product", criteria.product());
        appendLikeFilter(whereSql, params, "LOWER(COALESCE(pm.full_name, ''))", "customerNamePattern", criteria.customerName());
        appendGreaterThanOrEqualFilter(whereSql, params, "c.contract_date", "contractDateFrom", criteria.contractDateFrom());
        appendLessThanOrEqualFilter(whereSql, params, "c.contract_date", "contractDateTo", criteria.contractDateTo());
        appendGreaterThanOrEqualFilter(whereSql, params, "c.loan_amount", "minimumLoanAmount", criteria.minimumLoanAmount());
        appendLessThanOrEqualFilter(whereSql, params, "c.loan_amount", "maximumLoanAmount", criteria.maximumLoanAmount());

        params.addValue("size", criteria.size());
        params.addValue("offset", (long) criteria.page() * criteria.size());

        return new QueryParts(whereSql.toString(), params);
    }

    private void appendAccessFilter(StringBuilder whereSql, MapSqlParameterSource params, ReportAccessScope accessScope) {
        params.addValue("fullAccess", accessScope == null || accessScope.fullAccess());
        params.addValue("scopeUserId", accessScope == null ? null : accessScope.userId());
        whereSql.append("""
            AND (
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
            """);
    }

    private void appendKeywordFilter(StringBuilder whereSql, MapSqlParameterSource params, String keyword) {
        if (keyword == null) {
            return;
        }

        whereSql.append("""
            AND (
                LOWER(COALESCE(c.contract_number, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(pm.full_name, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(c.borrower_code, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(c.guarantor_code, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(c.contract_type, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(c.area_code, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(c.registration_number, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(c.vehicle_make, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(a.registration_number, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(a.engine_number, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(a.chassis_number, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(pm.contact_number, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(pm.city, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(pm.pan_number, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(gm.full_name, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(gm.contact_number, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(gm.city, '')) LIKE :keywordPattern
                OR LOWER(COALESCE(gm.pan_number, '')) LIKE :keywordPattern
            )
            """);
        params.addValue("keywordPattern", "%" + keyword.toLowerCase() + "%");
    }

    private void appendEqualsFilter(StringBuilder whereSql, MapSqlParameterSource params, String column, String paramName, Object value) {
        appendFilter(whereSql, params, column + " = :" + paramName, paramName, value);
    }

    private void appendStatusFilter(StringBuilder whereSql, MapSqlParameterSource params, String paramName, String value) {
        String status = normalizeStatus(value);
        if (status == null) {
            return;
        }

        whereSql.append("AND UPPER(TRIM(COALESCE(c.status, ''))) = :").append(paramName).append("\n");
        params.addValue(paramName, status);
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "DRAFT" -> "D";
            case "SEND_FOR_EDIT", "SEND FOR EDIT", "SUBMITTED_FOR_EDIT" -> "E";
            case "ACTIVE" -> "Y";
            case "INACTIVE", "IN ACTIVE" -> "N";
            default -> normalized;
        };
    }

    private String normalizeUpper(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private String normalizeLower(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private void appendGreaterThanOrEqualFilter(StringBuilder whereSql, MapSqlParameterSource params, String column, String paramName, Object value) {
        appendFilter(whereSql, params, column + " >= :" + paramName, paramName, value);
    }

    private void appendLessThanOrEqualFilter(StringBuilder whereSql, MapSqlParameterSource params, String column, String paramName, Object value) {
        appendFilter(whereSql, params, column + " <= :" + paramName, paramName, value);
    }

    private void appendLikeFilter(StringBuilder whereSql, MapSqlParameterSource params, String columnExpression, String paramName, String value) {
        if (value == null) {
            return;
        }

        whereSql.append("AND ").append(columnExpression).append(" LIKE :").append(paramName).append("\n");
        params.addValue(paramName, "%" + value.toLowerCase() + "%");
    }

    private void appendFilter(StringBuilder whereSql, MapSqlParameterSource params, String sql, String paramName, Object value) {
        if (value == null) {
            return;
        }

        whereSql.append("AND ").append(sql).append("\n");
        params.addValue(paramName, value);
    }

    private String findSql(ContractListCriteria criteria, String whereSql) {
        return FIND_SQL_PREFIX
            + whereSql
            + orderBySql(criteria)
            + """
            LIMIT :size
            OFFSET :offset
            """;
    }

    private String orderBySql(ContractListCriteria criteria) {
        ContractListSortField sortField = ContractListSortField.fromApiName(criteria.sortColumn());
        ContractListSortDirection sortDirection = ContractListSortDirection.fromApiValue(criteria.sortDirection());
        String primarySort = sortField.sqlColumn() + " " + sortDirection.sqlKeyword();
        if (sortField == ContractListSortField.CONTRACT_ID) {
            return "ORDER BY " + primarySort + "\n";
        }
        return "ORDER BY " + primarySort + ", c.contract_id DESC\n";
    }

    private record QueryParts(String whereSql, MapSqlParameterSource params) {
    }
}
