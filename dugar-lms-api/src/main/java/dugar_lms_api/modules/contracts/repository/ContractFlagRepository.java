package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.ContractFlagDto;
import dugar_lms_api.modules.contracts.dto.ContractFlagMasterDto;
import dugar_lms_api.modules.contracts.dto.ContractFlagSelectionDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public class ContractFlagRepository {

    private static final String LIST_ACTIVE_MASTER_SQL = """
        SELECT
            contract_flag_master_id,
            flag_code,
            flag_name,
            display_order
        FROM contract_flag_master
        WHERE is_active = TRUE
        ORDER BY display_order, flag_name
        """;

    private static final String FIND_BY_CONTRACT_SQL = """
        SELECT
            master.contract_flag_master_id,
            master.flag_code,
            master.flag_name
        FROM contract_flags contract_flag
        JOIN contract_flag_master master
          ON master.contract_flag_master_id = contract_flag.contract_flag_master_id
        WHERE contract_flag.contract_id = :contractId
        ORDER BY master.display_order, master.flag_name
        """;

    private static final String FIND_ACTIVE_IDS_SQL = """
        SELECT contract_flag_master_id
        FROM contract_flag_master
        WHERE is_active = TRUE
          AND contract_flag_master_id IN (:flagIds)
        """;

    private static final String DELETE_BY_CONTRACT_SQL = """
        DELETE FROM contract_flags
        WHERE contract_id = :contractId
        """;

    private static final String INSERT_FLAG_SQL = """
        INSERT INTO contract_flags (
            contract_id,
            contract_flag_master_id,
            created_by,
            updated_by
        )
        VALUES (
            :contractId,
            :contractFlagMasterId,
            :auditUser,
            :auditUser
        )
        """;

    private static final RowMapper<ContractFlagMasterDto> MASTER_ROW_MAPPER = (rs, rowNum) -> new ContractFlagMasterDto(
        rs.getLong("contract_flag_master_id"),
        rs.getString("flag_code"),
        rs.getString("flag_name"),
        rs.getObject("display_order", Integer.class)
    );

    private static final RowMapper<ContractFlagDto> FLAG_ROW_MAPPER = (rs, rowNum) -> new ContractFlagDto(
        rs.getLong("contract_flag_master_id"),
        rs.getString("flag_code"),
        rs.getString("flag_name")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ContractFlagRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ContractFlagMasterDto> findActiveMasterFlags() {
        return jdbcTemplate.query(LIST_ACTIVE_MASTER_SQL, MASTER_ROW_MAPPER);
    }

    public List<ContractFlagDto> findByContractId(Long contractId) {
        return jdbcTemplate.query(
            FIND_BY_CONTRACT_SQL,
            new MapSqlParameterSource("contractId", contractId),
            FLAG_ROW_MAPPER
        );
    }

    public Set<Long> findActiveFlagIds(Collection<Long> flagIds) {
        if (flagIds == null || flagIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbcTemplate.queryForList(
            FIND_ACTIVE_IDS_SQL,
            new MapSqlParameterSource("flagIds", flagIds),
            Long.class
        ));
    }

    public void replaceContractFlags(Long contractId, List<ContractFlagSelectionDto> flags, String auditUser) {
        jdbcTemplate.update(DELETE_BY_CONTRACT_SQL, new MapSqlParameterSource("contractId", contractId));
        if (flags == null || flags.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] batch = flags.stream()
            .map(flag -> new MapSqlParameterSource()
                .addValue("contractId", contractId)
                .addValue("contractFlagMasterId", flag.contractFlagMasterId())
                .addValue("auditUser", auditUser))
            .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(INSERT_FLAG_SQL, batch);
    }
}
