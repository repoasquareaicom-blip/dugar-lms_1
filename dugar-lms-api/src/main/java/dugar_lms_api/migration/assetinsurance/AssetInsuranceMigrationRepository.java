package dugar_lms_api.migration.assetinsurance;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class AssetInsuranceMigrationRepository {

    private static final String FIND_CONTRACT_ID_SQL = """
        SELECT contract_id
        FROM contracts
        WHERE UPPER(TRIM(contract_type)) = :contractType
          AND UPPER(TRIM(contract_number)) = :contractNumber
        ORDER BY contract_id
        LIMIT 1
        """;

    private static final String FIND_ASSETS_BY_CONTRACT_SQL = """
        SELECT
            asset_id,
            registration_number,
            engine_number,
            chassis_number,
            source_row_hash
        FROM assets
        WHERE contract_id = :contractId
        ORDER BY asset_id
        """;

    private static final String BASE_INSURANCE_COLUMNS = """
        asset_insurance_id,
        asset_id,
        insurance_company_code,
        policy_number,
        cover_note_number,
        policy_date,
        valid_from,
        valid_to,
        policy_by,
        premium_amount,
        source_row_hash
        """;

    private static final String FIND_BY_POLICY_SQL = """
        SELECT
        """ + BASE_INSURANCE_COLUMNS + """
        FROM asset_insurances
        WHERE asset_id = :assetId
          AND UPPER(TRIM(policy_number)) = :policyNumber
        ORDER BY asset_insurance_id
        """;

    private static final String FIND_BY_FALLBACK_SQL = """
        SELECT
        """ + BASE_INSURANCE_COLUMNS + """
        FROM asset_insurances
        WHERE asset_id = :assetId
          AND COALESCE(UPPER(TRIM(insurance_company_code)), '') = COALESCE(:insuranceCompanyCode, '')
          AND COALESCE(policy_date, DATE '0001-01-01') = COALESCE(:policyDate, DATE '0001-01-01')
          AND COALESCE(valid_from, DATE '0001-01-01') = COALESCE(:validFrom, DATE '0001-01-01')
          AND COALESCE(valid_to, DATE '0001-01-01') = COALESCE(:validTo, DATE '0001-01-01')
          AND COALESCE(premium_amount, -1) = COALESCE(:premiumAmount, -1)
          AND COALESCE(UPPER(TRIM(cover_note_number)), '') = COALESCE(:coverNoteNumber, '')
        ORDER BY asset_insurance_id
        """;

    private static final String INSERT_SQL = """
        INSERT INTO asset_insurances (
            asset_id,
            insurance_company_code,
            policy_number,
            cover_note_number,
            policy_date,
            valid_from,
            valid_to,
            policy_by,
            premium_amount,
            source_row_hash
        )
        VALUES (
            :assetId,
            :insuranceCompanyCode,
            :policyNumber,
            :coverNoteNumber,
            :policyDate,
            :validFrom,
            :validTo,
            :policyBy,
            :premiumAmount,
            :sourceRowHash
        )
        RETURNING asset_insurance_id
        """;

    private static final String TABLE_COLUMNS_SQL = """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = :tableName
        """;

    private static final RowMapper<AssetCandidate> ASSET_ROW_MAPPER = (rs, rowNum) -> new AssetCandidate(
        rs.getLong("asset_id"),
        rs.getString("registration_number"),
        rs.getString("engine_number"),
        rs.getString("chassis_number"),
        rs.getString("source_row_hash")
    );

    private static final RowMapper<AssetInsuranceExistingRecord> INSURANCE_ROW_MAPPER = (rs, rowNum) -> new AssetInsuranceExistingRecord(
        rs.getLong("asset_insurance_id"),
        rs.getLong("asset_id"),
        rs.getString("insurance_company_code"),
        rs.getString("policy_number"),
        rs.getString("cover_note_number"),
        rs.getObject("policy_date", java.time.LocalDate.class),
        rs.getObject("valid_from", java.time.LocalDate.class),
        rs.getObject("valid_to", java.time.LocalDate.class),
        rs.getString("policy_by"),
        rs.getBigDecimal("premium_amount"),
        rs.getString("source_row_hash")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public AssetInsuranceMigrationRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<Long> findContractId(String contractType, String contractNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractType", contractType)
            .addValue("contractNumber", contractNumber);

        return namedParameterJdbcTemplate.queryForList(FIND_CONTRACT_ID_SQL, params, Long.class).stream().findFirst();
    }

    public List<AssetCandidate> findAssetsByContractId(Long contractId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractId", contractId);

        return namedParameterJdbcTemplate.query(FIND_ASSETS_BY_CONTRACT_SQL, params, ASSET_ROW_MAPPER);
    }

    public List<AssetInsuranceExistingRecord> findByPolicyNumber(Long assetId, String normalizedPolicyNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("assetId", assetId)
            .addValue("policyNumber", normalizedPolicyNumber);

        return namedParameterJdbcTemplate.query(FIND_BY_POLICY_SQL, params, INSURANCE_ROW_MAPPER);
    }

    public List<AssetInsuranceExistingRecord> findByFallbackKey(AssetInsuranceRecord record) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("assetId", record.assetId())
            .addValue("insuranceCompanyCode", normalize(record.insuranceCompanyCode()))
            .addValue("policyDate", record.policyDate())
            .addValue("validFrom", record.validFrom())
            .addValue("validTo", record.validTo())
            .addValue("premiumAmount", record.premiumAmount())
            .addValue("coverNoteNumber", normalize(record.coverNoteNumber()));

        return namedParameterJdbcTemplate.query(FIND_BY_FALLBACK_SQL, params, INSURANCE_ROW_MAPPER);
    }

    public Long insert(AssetInsuranceRecord record) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("assetId", record.assetId())
            .addValue("insuranceCompanyCode", record.insuranceCompanyCode())
            .addValue("policyNumber", record.policyNumber())
            .addValue("coverNoteNumber", record.coverNoteNumber())
            .addValue("policyDate", record.policyDate())
            .addValue("validFrom", record.validFrom())
            .addValue("validTo", record.validTo())
            .addValue("policyBy", record.policyBy())
            .addValue("premiumAmount", record.premiumAmount())
            .addValue("sourceRowHash", record.sourceRowHash());

        return namedParameterJdbcTemplate.queryForObject(INSERT_SQL, params, Long.class);
    }

    public Set<String> tableColumns(String tableName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tableName", tableName);

        return new LinkedHashSet<>(namedParameterJdbcTemplate.queryForList(TABLE_COLUMNS_SQL, params, String.class));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
