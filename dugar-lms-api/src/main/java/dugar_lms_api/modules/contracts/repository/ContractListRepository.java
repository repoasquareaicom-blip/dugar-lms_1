package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.ContractListDto;
import dugar_lms_api.modules.contracts.service.ContractListCriteria;
import dugar_lms_api.modules.contracts.service.ContractListSortDirection;
import dugar_lms_api.modules.contracts.service.ContractListSortField;
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
        COALESCE(NULLIF(TRIM(c.legacy_contract_number), ''), NULLIF(TRIM(c.contract_number), ''), '-') AS legacy_contract_number,
        COALESCE(NULLIF(TRIM(c.contract_type), ''), '-') AS product,
        COALESCE(NULLIF(TRIM(c.area_code), ''), '-') AS branch,
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
        """;

    private static final String FIND_SQL_PREFIX = """
        SELECT
        """ + CONTRACT_LIST_COLUMNS + FROM_SQL;

    private static final String COUNT_SQL = """
        SELECT COUNT(*)
        """ + FROM_SQL;

    private static final RowMapper<ContractListDto> CONTRACT_LIST_ROW_MAPPER = (rs, rowNum) -> new ContractListDto(
        rs.getLong("contract_id"),
        rs.getString("contract_number"),
        rs.getString("legacy_contract_number"),
        rs.getString("product"),
        rs.getString("branch"),
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
        QueryParts queryParts = queryParts(criteria);
        return namedParameterJdbcTemplate.query(findSql(criteria, queryParts.whereSql()), queryParts.params(), CONTRACT_LIST_ROW_MAPPER);
    }

    public long count(ContractListCriteria criteria) {
        QueryParts queryParts = queryParts(criteria);
        Long total = namedParameterJdbcTemplate.queryForObject(COUNT_SQL + queryParts.whereSql(), queryParts.params(), Long.class);
        return total == null ? 0 : total;
    }

    private QueryParts queryParts(ContractListCriteria criteria) {
        StringBuilder whereSql = new StringBuilder("""
            WHERE c.is_active = TRUE
              AND EXISTS (
                  SELECT 1
                  FROM contract_repayment_structures repayment_exists
                  WHERE repayment_exists.contract_id = c.contract_id
              )
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        appendKeywordFilter(whereSql, params, criteria.keyword());
        appendEqualsFilter(whereSql, params, "c.area_code", "branch", criteria.branch());
        appendEqualsFilter(whereSql, params, "c.status", "status", criteria.status());
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
