package dugar_lms_api.modules.assets.repository;

import dugar_lms_api.modules.assets.dto.AssetCreateDto;
import dugar_lms_api.modules.assets.dto.AssetDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class AssetRepository {

    private static final String ASSET_COLUMNS = """
        asset_id,
        contract_id,
        oracle_contract_type,
        oracle_contract_no,
        finance_type,
        vehicle_type_code,
        registration_number,
        engine_number,
        chassis_number,
        manufacture_year,
        equipment_value,
        security_offered,
        owner_serial_no,
        source_table,
        source_row_hash,
        created_at,
        updated_at
        """;

    private static final String FIND_BY_ID_SQL = """
        SELECT
        """ + ASSET_COLUMNS + """
        FROM assets
        WHERE asset_id = :assetId
        """;

    private static final String FIND_BY_ORACLE_CONTRACT_SQL = """
        SELECT
        """ + ASSET_COLUMNS + """
        FROM assets
        WHERE oracle_contract_type = :oracleContractType
          AND oracle_contract_no = :oracleContractNo
        ORDER BY asset_id
        """;

    private static final String FIND_CONTRACT_ID_SQL = """
        SELECT contract_id
        FROM contracts
        WHERE UPPER(TRIM(contract_type)) = :contractType
          AND UPPER(TRIM(contract_number)) = :contractNumber
        ORDER BY contract_id
        LIMIT 1
        """;

    private static final String FIND_BY_CHASSIS_BUSINESS_KEY_SQL = """
        SELECT
        """ + ASSET_COLUMNS + """
        FROM assets
        WHERE contract_id = :contractId
          AND UPPER(TRIM(chassis_number)) = :normalizedValue
        ORDER BY asset_id
        """;

    private static final String FIND_BY_ENGINE_BUSINESS_KEY_SQL = """
        SELECT
        """ + ASSET_COLUMNS + """
        FROM assets
        WHERE contract_id = :contractId
          AND UPPER(TRIM(engine_number)) = :normalizedValue
        ORDER BY asset_id
        """;

    private static final String FIND_BY_REGISTRATION_BUSINESS_KEY_SQL = """
        SELECT
        """ + ASSET_COLUMNS + """
        FROM assets
        WHERE contract_id = :contractId
          AND REPLACE(REPLACE(UPPER(TRIM(registration_number)), ' ', ''), '-', '') = :normalizedValue
        ORDER BY asset_id
        """;

    private static final String FIND_BY_SOURCE_ROW_HASH_SQL = """
        SELECT
        """ + ASSET_COLUMNS + """
        FROM assets
        WHERE contract_id = :contractId
          AND source_row_hash = :normalizedValue
        ORDER BY asset_id
        """;

    private static final String INSERT_SQL = """
        INSERT INTO assets (
            contract_id,
            oracle_contract_type,
            oracle_contract_no,
            finance_type,
            vehicle_type_code,
            registration_number,
            engine_number,
            chassis_number,
            manufacture_year,
            equipment_value,
            security_offered,
            owner_serial_no,
            source_table,
            source_row_hash
        )
        VALUES (
            :contractId,
            :oracleContractType,
            :oracleContractNo,
            :financeType,
            :vehicleTypeCode,
            :registrationNumber,
            :engineNumber,
            :chassisNumber,
            :manufactureYear,
            :equipmentValue,
            :securityOffered,
            :ownerSerialNo,
            :sourceTable,
            :sourceRowHash
        )
        RETURNING asset_id
        """;

    private static final String TABLE_COLUMNS_SQL = """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = :tableName
        """;

    private static final RowMapper<AssetDto> ASSET_ROW_MAPPER = (rs, rowNum) -> new AssetDto(
        rs.getLong("asset_id"),
        rs.getLong("contract_id"),
        rs.getString("oracle_contract_type"),
        rs.getString("oracle_contract_no"),
        rs.getString("finance_type"),
        rs.getString("vehicle_type_code"),
        rs.getString("registration_number"),
        rs.getString("engine_number"),
        rs.getString("chassis_number"),
        rs.getString("manufacture_year"),
        rs.getBigDecimal("equipment_value"),
        rs.getString("security_offered"),
        rs.getString("owner_serial_no"),
        rs.getString("source_table"),
        rs.getString("source_row_hash"),
        rs.getObject("created_at", java.time.LocalDateTime.class),
        rs.getObject("updated_at", java.time.LocalDateTime.class)
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public AssetRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<AssetDto> findById(Long assetId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("assetId", assetId);

        List<AssetDto> assets = namedParameterJdbcTemplate.query(FIND_BY_ID_SQL, params, ASSET_ROW_MAPPER);
        return assets.stream().findFirst();
    }

    public List<AssetDto> findByOracleContract(String oracleContractType, String oracleContractNo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("oracleContractType", oracleContractType)
            .addValue("oracleContractNo", oracleContractNo);

        return namedParameterJdbcTemplate.query(FIND_BY_ORACLE_CONTRACT_SQL, params, ASSET_ROW_MAPPER);
    }

    public Optional<Long> findContractId(String normalizedContractType, String normalizedContractNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractType", normalizedContractType)
            .addValue("contractNumber", normalizedContractNumber);

        List<Long> contractIds = namedParameterJdbcTemplate.queryForList(FIND_CONTRACT_ID_SQL, params, Long.class);
        return contractIds.stream().findFirst();
    }

    public List<AssetDto> findByBusinessKey(Long contractId, String businessKeyType, String normalizedValue) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractId", contractId)
            .addValue("normalizedValue", normalizedValue);

        return namedParameterJdbcTemplate.query(businessKeySql(businessKeyType), params, ASSET_ROW_MAPPER);
    }

    public Long insert(AssetCreateDto asset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractId", asset.contractId())
            .addValue("oracleContractType", asset.oracleContractType())
            .addValue("oracleContractNo", asset.oracleContractNo())
            .addValue("financeType", asset.financeType())
            .addValue("vehicleTypeCode", asset.vehicleTypeCode())
            .addValue("registrationNumber", asset.registrationNumber())
            .addValue("engineNumber", asset.engineNumber())
            .addValue("chassisNumber", asset.chassisNumber())
            .addValue("manufactureYear", asset.manufactureYear())
            .addValue("equipmentValue", asset.equipmentValue())
            .addValue("securityOffered", asset.securityOffered())
            .addValue("ownerSerialNo", asset.ownerSerialNo())
            .addValue("sourceTable", asset.sourceTable())
            .addValue("sourceRowHash", asset.sourceRowHash());

        return namedParameterJdbcTemplate.queryForObject(INSERT_SQL, params, Long.class);
    }

    public Set<String> tableColumns(String tableName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tableName", tableName);

        return new LinkedHashSet<>(namedParameterJdbcTemplate.queryForList(TABLE_COLUMNS_SQL, params, String.class));
    }

    private String businessKeySql(String businessKeyType) {
        return switch (businessKeyType) {
            case "CHASSIS" -> FIND_BY_CHASSIS_BUSINESS_KEY_SQL;
            case "ENGINE" -> FIND_BY_ENGINE_BUSINESS_KEY_SQL;
            case "REGISTRATION" -> FIND_BY_REGISTRATION_BUSINESS_KEY_SQL;
            case "SOURCE_ROW_HASH" -> FIND_BY_SOURCE_ROW_HASH_SQL;
            default -> throw new IllegalArgumentException("Unsupported asset business key type: " + businessKeyType);
        };
    }
}
