package dugar_lms_api.modules.contracts.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContractWorkflowRepository {

    private static final String UPDATE_WORKFLOW_SQL = """
        UPDATE contracts
        SET
            status = :status,
            is_draft = :isDraft,
            is_active = TRUE,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP
        WHERE contract_id = :contractId
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ContractWorkflowRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public void updateWorkflow(Long contractId, String status, boolean isDraft, String updatedBy) {
        namedParameterJdbcTemplate.update(
            UPDATE_WORKFLOW_SQL,
            new MapSqlParameterSource()
                .addValue("contractId", contractId)
                .addValue("status", status)
                .addValue("isDraft", isDraft)
                .addValue("updatedBy", updatedBy)
        );
    }
}
