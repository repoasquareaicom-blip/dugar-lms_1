package dugar_lms_api.migration.repaymentstructure;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class RepaymentStructureMigrationRepository {

    private static final String FIND_CONTRACT_IDS_SQL = """
        SELECT contract_id
        FROM contracts
        WHERE UPPER(TRIM(contract_type)) = :contractType
          AND UPPER(TRIM(contract_number)) = :contractNumber
        ORDER BY contract_id
        """;

    private static final String FIND_EXISTING_SQL = """
        SELECT
            repayment_structure_id,
            contract_id,
            sequence_no,
            number_of_installments,
            installment_amount
        FROM contract_repayment_structures
        WHERE contract_id = :contractId
          AND sequence_no = :sequenceNo
        """;

    private static final String INSERT_SQL = """
        INSERT INTO contract_repayment_structures (
            contract_id,
            sequence_no,
            number_of_installments,
            installment_amount
        )
        VALUES (
            :contractId,
            :sequenceNo,
            :numberOfInstallments,
            :installmentAmount
        )
        RETURNING repayment_structure_id
        """;

    private static final String TABLE_COLUMNS_SQL = """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = :tableName
        """;

    private static final RowMapper<ExistingRepaymentStructure> EXISTING_ROW_MAPPER = (rs, rowNum) -> new ExistingRepaymentStructure(
        rs.getLong("repayment_structure_id"),
        rs.getLong("contract_id"),
        rs.getInt("sequence_no"),
        rs.getInt("number_of_installments"),
        rs.getBigDecimal("installment_amount")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public RepaymentStructureMigrationRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<Long> findContractIds(String contractType, String contractNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractType", contractType)
            .addValue("contractNumber", contractNumber);

        return namedParameterJdbcTemplate.queryForList(FIND_CONTRACT_IDS_SQL, params, Long.class);
    }

    public Optional<ExistingRepaymentStructure> findExisting(Long contractId, Integer sequenceNo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractId", contractId)
            .addValue("sequenceNo", sequenceNo);

        return namedParameterJdbcTemplate.query(FIND_EXISTING_SQL, params, EXISTING_ROW_MAPPER).stream().findFirst();
    }

    public Long insert(RepaymentStructureRecord record) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractId", record.contractId())
            .addValue("sequenceNo", record.sequenceNo())
            .addValue("numberOfInstallments", record.numberOfInstallments())
            .addValue("installmentAmount", record.installmentAmount());

        return namedParameterJdbcTemplate.queryForObject(INSERT_SQL, params, Long.class);
    }

    public Set<String> tableColumns(String tableName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tableName", tableName);

        return new LinkedHashSet<>(namedParameterJdbcTemplate.queryForList(TABLE_COLUMNS_SQL, params, String.class));
    }
}
