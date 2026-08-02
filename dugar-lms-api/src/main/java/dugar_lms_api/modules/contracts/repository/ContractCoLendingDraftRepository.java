package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.ContractCoLendingDraftDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ContractCoLendingDraftRepository {

    private static final String FIND_CO_LENDING_SQL = """
        SELECT
            c.contract_id,
            cd.co_lending_type,
            cd.co_lender_name,
            cd.co_lending_security_deposit,
            cd.co_lending_contribution_share_percent,
            cd.co_lending_emi_share_percent,
            cd.co_lending_revenue_share_percent,
            cd.co_lending_risk_share_percent
        FROM contracts c
        LEFT JOIN contract_details cd
          ON cd.contract_id = c.contract_id
        WHERE c.contract_id = :contractId
        ORDER BY cd.contract_detail_id
        LIMIT 1
        """;

    private static final String FIND_CONTRACT_DETAIL_ID_SQL = """
        SELECT contract_detail_id
        FROM contract_details
        WHERE contract_id = :contractId
        ORDER BY contract_detail_id
        LIMIT 1
        """;

    private static final String INSERT_CONTRACT_DETAIL_SQL = """
        INSERT INTO contract_details (
            contract_id,
            co_lending_type,
            co_lender_name,
            co_lending_security_deposit,
            co_lending_contribution_share_percent,
            co_lending_emi_share_percent,
            co_lending_revenue_share_percent,
            co_lending_risk_share_percent,
            created_by,
            is_active
        )
        VALUES (
            :contractId,
            :coLendingType,
            :coLenderName,
            :securityDepositAmount,
            :contributionSharePercent,
            :emiSharePercent,
            :revenueSharePercent,
            :riskSharePercent,
            :updatedBy,
            TRUE
        )
        """;

    private static final String UPDATE_CONTRACT_DETAIL_SQL = """
        UPDATE contract_details
        SET
            co_lending_type = :coLendingType,
            co_lender_name = :coLenderName,
            co_lending_security_deposit = :securityDepositAmount,
            co_lending_contribution_share_percent = :contributionSharePercent,
            co_lending_emi_share_percent = :emiSharePercent,
            co_lending_revenue_share_percent = :revenueSharePercent,
            co_lending_risk_share_percent = :riskSharePercent,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP,
            is_active = TRUE
        WHERE contract_detail_id = :contractDetailId
        """;

    private static final RowMapper<ContractCoLendingDraftDto> ROW_MAPPER = (rs, rowNum) -> new ContractCoLendingDraftDto(
        rs.getLong("contract_id"),
        rs.getString("co_lending_type"),
        rs.getString("co_lender_name"),
        rs.getBigDecimal("co_lending_security_deposit"),
        rs.getBigDecimal("co_lending_contribution_share_percent"),
        rs.getBigDecimal("co_lending_emi_share_percent"),
        rs.getBigDecimal("co_lending_revenue_share_percent"),
        rs.getBigDecimal("co_lending_risk_share_percent")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ContractCoLendingDraftRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<ContractCoLendingDraftDto> findByContractId(Long contractId) {
        List<ContractCoLendingDraftDto> rows = namedParameterJdbcTemplate.query(
            FIND_CO_LENDING_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public void upsertContractDetail(ContractCoLendingDraftDto coLending, String updatedBy) {
        MapSqlParameterSource lookupParams = new MapSqlParameterSource().addValue("contractId", coLending.contractId());
        List<Long> detailIds = namedParameterJdbcTemplate.queryForList(FIND_CONTRACT_DETAIL_ID_SQL, lookupParams, Long.class);

        MapSqlParameterSource params = params(coLending).addValue("updatedBy", updatedBy);
        if (detailIds.isEmpty()) {
            namedParameterJdbcTemplate.update(INSERT_CONTRACT_DETAIL_SQL, params);
            return;
        }

        namedParameterJdbcTemplate.update(
            UPDATE_CONTRACT_DETAIL_SQL,
            params.addValue("contractDetailId", detailIds.get(0))
        );
    }

    private MapSqlParameterSource params(ContractCoLendingDraftDto coLending) {
        return new MapSqlParameterSource()
            .addValue("contractId", coLending.contractId())
            .addValue("coLendingType", clean(coLending.coLendingType()))
            .addValue("coLenderName", clean(coLending.coLenderName()))
            .addValue("securityDepositAmount", coLending.securityDepositAmount())
            .addValue("contributionSharePercent", coLending.contributionSharePercent())
            .addValue("emiSharePercent", coLending.emiSharePercent())
            .addValue("revenueSharePercent", coLending.revenueSharePercent())
            .addValue("riskSharePercent", coLending.riskSharePercent());
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
