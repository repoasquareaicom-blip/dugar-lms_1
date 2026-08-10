package dugar_lms_api.modules.reports.demandlist;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DemandListRepository {

    private static final String SOURCE_SQL = """
        WITH receipt_base AS (
            SELECT
                vd.voucher_detail_id,
                GREATEST(COALESCE(vd.credit_amount, 0), 0) AS receipt_amount,
                vh.contract_id,
                NULLIF(UPPER(TRIM(vh.contract_number)), '') AS header_contract_number,
                NULLIF(UPPER(TRIM(vd.loan_reference)), '') AS loan_reference,
                NULLIF(UPPER(TRIM(vd.sub_ledger_code)), '') AS sub_ledger_code
            FROM voucher_headers vh
            JOIN voucher_details vd
              ON vd.voucher_header_id = vh.voucher_header_id
            WHERE UPPER(TRIM(COALESCE(vh.status, ''))) = 'AUTHORISED'
              AND vh.voucher_date <= :asOnDate
              AND TRIM(COALESCE(vd.ledger_code, '')) IN ('3001', '4201')
              AND UPPER(TRIM(COALESCE(vd.voucher_type, vh.voucher_type, ''))) <> 'HJ'
              AND COALESCE(vd.credit_amount, 0) > 0
        ),
        receipt_matches AS (
            SELECT c.contract_id, rb.voucher_detail_id, rb.receipt_amount
            FROM receipt_base rb
            JOIN contracts c
              ON c.contract_id = rb.contract_id
            WHERE c.is_active = TRUE
              AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'

            UNION

            SELECT c.contract_id, rb.voucher_detail_id, rb.receipt_amount
            FROM receipt_base rb
            JOIN contracts c
              ON rb.header_contract_number IN (
                  UPPER(TRIM(COALESCE(c.contract_number, ''))),
                  UPPER(TRIM(COALESCE(c.legacy_contract_number, '')))
              )
            WHERE rb.header_contract_number IS NOT NULL
              AND c.is_active = TRUE
              AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'

            UNION

            SELECT c.contract_id, rb.voucher_detail_id, rb.receipt_amount
            FROM receipt_base rb
            JOIN contracts c
              ON rb.loan_reference IN (
                  UPPER(TRIM(COALESCE(c.contract_number, ''))),
                  UPPER(TRIM(COALESCE(c.legacy_contract_number, '')))
              )
            WHERE rb.loan_reference IS NOT NULL
              AND c.is_active = TRUE
              AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'

            UNION

            SELECT c.contract_id, rb.voucher_detail_id, rb.receipt_amount
            FROM receipt_base rb
            JOIN contracts c
              ON rb.sub_ledger_code IN (
                  UPPER(TRIM(COALESCE(c.contract_number, ''))),
                  UPPER(TRIM(COALESCE(c.legacy_contract_number, '')))
              )
            WHERE rb.sub_ledger_code IS NOT NULL
              AND c.is_active = TRUE
              AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
        ),
        authorised_receipts AS (
            SELECT
                contract_id,
                COALESCE(SUM(receipt_amount), 0) AS authorised_receipts
            FROM (
                SELECT DISTINCT contract_id, voucher_detail_id, receipt_amount
                FROM receipt_matches
            ) distinct_receipts
            GROUP BY contract_id
        )
        SELECT
            c.contract_id,
            COALESCE(NULLIF(TRIM(c.contract_number), ''), NULLIF(TRIM(c.legacy_contract_number), ''), c.contract_id::text) AS loan_number,
            NULLIF(TRIM(pm.full_name), '') AS borrower_name,
            NULLIF(TRIM(gm.full_name), '') AS guarantor_name,
            COALESCE(NULLIF(TRIM(a.product_type), ''), NULLIF(TRIM(c.contract_type), '')) AS product_type,
            NULLIF(TRIM(c.contract_type), '') AS contract_type,
            COALESCE(NULLIF(TRIM(a.vehicle_make), ''), NULLIF(TRIM(c.vehicle_make), ''), NULLIF(TRIM(c.equipment_model), '')) AS asset_description,
            NULLIF(TRIM(a.vehicle_type_code), '') AS vehicle_type_code,
            COALESCE(
                NULLIF(TRIM(a.registration_number), ''),
                NULLIF(TRIM(c.registration_number), ''),
                NULLIF(TRIM(CONCAT_WS(', ', NULLIF(TRIM(a.flat_no), ''), NULLIF(TRIM(a.apartment_no), ''), NULLIF(TRIM(a.street_name), ''), NULLIF(TRIM(a.area_name), ''), NULLIF(TRIM(a.property_city), ''))), '')
            ) AS registration_or_location,
            COALESCE(NULLIF(TRIM(a.owner_serial_no), ''), NULLIF(TRIM(cd.owner_serial_number), '')) AS owner_number,
            COALESCE(NULLIF(TRIM(a.deal_of_assets), ''), NULLIF(TRIM(a.finance_type), ''), NULLIF(TRIM(c.mode_of_payment), '')) AS usage,
            COALESCE(c.loan_amount, 0) AS loan_amount,
            COALESCE(c.finance_charges, 0) AS finance_charges,
            COALESCE(c.total_contract_value, 0) AS total_contract_value,
            c.first_emi_date,
            COALESCE(NULLIF(TRIM(c.payment_frequency), ''), NULLIF(TRIM(c.repayment_terms), ''), NULLIF(TRIM(c.mode_of_payment), '')) AS payment_frequency,
            NULLIF(TRIM(c.area_code), '') AS area,
            COALESCE(NULLIF(TRIM(cd.branch_collection_tool_by), ''), NULLIF(TRIM(cd.ho_collection_tool_by), ''), NULLIF(TRIM(cd.loan_referred_by), '')) AS field_officer,
            COALESCE(ar.authorised_receipts, 0) AS authorised_receipts
        FROM contracts c
        LEFT JOIN party_masters pm
          ON UPPER(TRIM(pm.party_code)) = UPPER(TRIM(c.borrower_code))
         AND pm.is_active = TRUE
        LEFT JOIN party_masters gm
          ON UPPER(TRIM(gm.party_code)) = UPPER(TRIM(c.guarantor_code))
         AND gm.is_active = TRUE
        LEFT JOIN contract_details cd
          ON cd.contract_id = c.contract_id
         AND cd.is_active = TRUE
        LEFT JOIN LATERAL (
            SELECT *
            FROM assets asset
            WHERE asset.contract_id = c.contract_id
            ORDER BY asset.asset_id
            LIMIT 1
        ) a ON TRUE
        LEFT JOIN authorised_receipts ar
          ON ar.contract_id = c.contract_id
        WHERE c.is_active = TRUE
          AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'
        """;

    private static final String REPAYMENT_SQL = """
        SELECT
            contract_id,
            sequence_no,
            number_of_installments,
            installment_amount
        FROM contract_repayment_structures
        WHERE contract_id IN (:contractIds)
        ORDER BY contract_id, sequence_no
        """;

    private static final RowMapper<DemandListSourceRow> SOURCE_ROW_MAPPER = (rs, rowNum) -> new DemandListSourceRow(
        rs.getLong("contract_id"),
        rs.getString("loan_number"),
        rs.getString("borrower_name"),
        rs.getString("guarantor_name"),
        rs.getString("product_type"),
        rs.getString("contract_type"),
        rs.getString("asset_description"),
        rs.getString("vehicle_type_code"),
        rs.getString("registration_or_location"),
        rs.getString("owner_number"),
        rs.getString("usage"),
        rs.getBigDecimal("loan_amount"),
        rs.getBigDecimal("finance_charges"),
        rs.getBigDecimal("total_contract_value"),
        rs.getObject("first_emi_date", java.time.LocalDate.class),
        rs.getString("payment_frequency"),
        rs.getString("area"),
        rs.getString("field_officer"),
        rs.getBigDecimal("authorised_receipts")
    );

    private static final RowMapper<DemandListRepaymentSlab> REPAYMENT_ROW_MAPPER = (rs, rowNum) -> new DemandListRepaymentSlab(
        rs.getLong("contract_id"),
        rs.getObject("sequence_no", Integer.class),
        rs.getObject("number_of_installments", Integer.class),
        rs.getBigDecimal("installment_amount")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DemandListRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<DemandListSourceRow> findSourceRows(DemandListRequest request) {
        QueryParts queryParts = queryParts(request);
        return namedParameterJdbcTemplate.query(queryParts.sql(), queryParts.params(), SOURCE_ROW_MAPPER);
    }

    public List<DemandListRepaymentSlab> findRepaymentSlabs(List<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return List.of();
        }
        return namedParameterJdbcTemplate.query(
            REPAYMENT_SQL,
            new MapSqlParameterSource().addValue("contractIds", contractIds),
            REPAYMENT_ROW_MAPPER
        );
    }

    private QueryParts queryParts(DemandListRequest request) {
        StringBuilder sql = new StringBuilder(SOURCE_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("asOnDate", request.asOnDate());

        appendEquals(sql, params, "c.area_code", "areaCode", request.areaCode());
        appendEquals(sql, params, "c.area_code", "branchId", request.branchId());
        appendEquals(sql, params, "COALESCE(cd.branch_collection_tool_by, cd.ho_collection_tool_by, cd.loan_referred_by)", "fieldOfficerCode", request.fieldOfficerCode());
        appendEquals(sql, params, "c.contract_type", "contractType", request.contractType());
        appendEquals(sql, params, "COALESCE(a.product_type, c.contract_type)", "productType", request.productType());
        appendContractNumber(sql, params, request.contractNumber());
        appendKeyword(sql, params, request.keyword());

        return new QueryParts(sql.toString(), params);
    }

    private void appendEquals(StringBuilder sql, MapSqlParameterSource params, String expression, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND UPPER(TRIM(COALESCE(").append(expression).append(", ''))) = :").append(name).append("\n");
        params.addValue(name, value.trim().toUpperCase());
    }

    private void appendKeyword(StringBuilder sql, MapSqlParameterSource params, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        sql.append("""
            AND (
                LOWER(COALESCE(c.contract_number, '')) LIKE :keyword
                OR LOWER(COALESCE(c.legacy_contract_number, '')) LIKE :keyword
                OR LOWER(COALESCE(pm.full_name, '')) LIKE :keyword
                OR LOWER(COALESCE(gm.full_name, '')) LIKE :keyword
                OR LOWER(COALESCE(c.borrower_code, '')) LIKE :keyword
                OR LOWER(COALESCE(c.guarantor_code, '')) LIKE :keyword
                OR LOWER(COALESCE(c.area_code, '')) LIKE :keyword
                OR LOWER(COALESCE(a.registration_number, '')) LIKE :keyword
                OR LOWER(COALESCE(c.registration_number, '')) LIKE :keyword
            )
            """);
        params.addValue("keyword", "%" + keyword.trim().toLowerCase() + "%");
    }

    private void appendContractNumber(StringBuilder sql, MapSqlParameterSource params, String contractNumber) {
        if (contractNumber == null || contractNumber.isBlank()) {
            return;
        }
        sql.append("""
            AND (
                LOWER(COALESCE(c.contract_number, '')) LIKE :contractNumber
                OR LOWER(COALESCE(c.legacy_contract_number, '')) LIKE :contractNumber
            )
            """);
        params.addValue("contractNumber", "%" + contractNumber.trim().toLowerCase() + "%");
    }

    private record QueryParts(String sql, MapSqlParameterSource params) {
    }
}
