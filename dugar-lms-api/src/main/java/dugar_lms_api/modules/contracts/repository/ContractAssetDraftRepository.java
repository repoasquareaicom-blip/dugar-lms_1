package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.ContractAssetDraftDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ContractAssetDraftRepository {

    private static final String FIND_SQL = """
        SELECT
            a.contract_id,
            a.asset_id,
            ai.asset_insurance_id,
            a.asset_secured,
            a.product_type,
            a.vehicle_type_code,
            a.deal_of_assets,
            COALESCE(NULLIF(TRIM(a.vehicle_make), ''), NULLIF(TRIM(c.vehicle_make), '')) AS vehicle_make,
            COALESCE(NULLIF(TRIM(a.version), ''), NULLIF(TRIM(c.equipment_model), '')) AS version,
            a.manufacture_year,
            COALESCE(NULLIF(TRIM(a.owner_serial_no), ''), NULLIF(TRIM(cd.owner_serial_number), '')) AS owner_serial_no,
            COALESCE(NULLIF(TRIM(a.registration_number), ''), NULLIF(TRIM(c.registration_number), '')) AS registration_number,
            a.fuel_type,
            a.kms_run,
            a.equipment_value AS market_value,
            COALESCE(NULLIF(TRIM(a.chassis_number), ''), NULLIF(TRIM(cd.chassis_number), '')) AS chassis_number,
            COALESCE(NULLIF(TRIM(a.engine_number), ''), NULLIF(TRIM(cd.engine_number), '')) AS engine_number,
            ai.idv_amount AS idv,
            ai.valid_to AS insurance_expiry,
            ai.insurance_company_code AS insurance_company,
            ai.premium_amount AS insurance_premium,
            a.property_type,
            a.flat_no,
            a.apartment_no,
            a.street_name,
            a.area_name,
            a.property_city,
            a.property_state,
            a.distance_from_office,
            a.any_rent_received,
            a.rent_amount,
            a.guideline_value,
            a.nature_of_business,
            a.date_of_incorporation,
            a.is_secured
        FROM assets a
        JOIN contracts c
          ON c.contract_id = a.contract_id
        LEFT JOIN contract_details cd
          ON cd.contract_id = a.contract_id
        LEFT JOIN LATERAL (
            SELECT *
            FROM asset_insurances insurance
            WHERE insurance.asset_id = a.asset_id
            ORDER BY insurance.asset_insurance_id
            LIMIT 1
        ) ai ON TRUE
        WHERE a.contract_id = :contractId
        ORDER BY a.asset_id
        LIMIT 1
        """;

    private static final String INSERT_ASSET_SQL = """
        INSERT INTO assets (
            contract_id,
            asset_secured,
            product_type,
            finance_type,
            vehicle_type_code,
            deal_of_assets,
            vehicle_make,
            version,
            manufacture_year,
            owner_serial_no,
            registration_number,
            fuel_type,
            kms_run,
            equipment_value,
            chassis_number,
            engine_number,
            security_offered,
            property_type,
            flat_no,
            apartment_no,
            street_name,
            area_name,
            property_city,
            property_state,
            distance_from_office,
            any_rent_received,
            rent_amount,
            guideline_value,
            nature_of_business,
            date_of_incorporation,
            is_secured,
            source_table,
            updated_at
        )
        VALUES (
            :contractId,
            :assetSecured,
            :productType,
            :dealOfAssets,
            :vehicleTypeCode,
            :dealOfAssets,
            :vehicleMake,
            :version,
            :manufactureYear,
            :ownerSerialNo,
            :registrationNumber,
            :fuelType,
            :kmsRun,
            :marketValue,
            :chassisNumber,
            :engineNumber,
            :assetSecured,
            :propertyType,
            :flatNo,
            :apartmentNo,
            :streetName,
            :areaName,
            :propertyCity,
            :propertyState,
            :distanceFromOffice,
            :anyRentReceived,
            :rentAmount,
            :guidelineValue,
            :natureOfBusiness,
            :dateOfIncorporation,
            :isSecured,
            'NEW_LMS_DRAFT',
            CURRENT_TIMESTAMP
        )
        RETURNING asset_id
        """;

    private static final String UPDATE_ASSET_SQL = """
        UPDATE assets
        SET
            asset_secured = :assetSecured,
            product_type = :productType,
            finance_type = :dealOfAssets,
            vehicle_type_code = :vehicleTypeCode,
            deal_of_assets = :dealOfAssets,
            vehicle_make = :vehicleMake,
            version = :version,
            manufacture_year = :manufactureYear,
            owner_serial_no = :ownerSerialNo,
            registration_number = :registrationNumber,
            fuel_type = :fuelType,
            kms_run = :kmsRun,
            equipment_value = :marketValue,
            chassis_number = :chassisNumber,
            engine_number = :engineNumber,
            security_offered = :assetSecured,
            property_type = :propertyType,
            flat_no = :flatNo,
            apartment_no = :apartmentNo,
            street_name = :streetName,
            area_name = :areaName,
            property_city = :propertyCity,
            property_state = :propertyState,
            distance_from_office = :distanceFromOffice,
            any_rent_received = :anyRentReceived,
            rent_amount = :rentAmount,
            guideline_value = :guidelineValue,
            nature_of_business = :natureOfBusiness,
            date_of_incorporation = :dateOfIncorporation,
            is_secured = :isSecured,
            updated_at = CURRENT_TIMESTAMP
        WHERE asset_id = :assetId
        """;

    private static final String UPDATE_CONTRACT_SUMMARY_SQL = """
        UPDATE contracts
        SET
            registration_number = :registrationNumber,
            vehicle_make = :vehicleMake,
            equipment_model = :version,
            updated_at = CURRENT_TIMESTAMP,
            updated_by = :updatedBy
        WHERE contract_id = :contractId
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
            engine_number,
            chassis_number,
            owner_serial_number,
            created_by,
            is_active
        )
        VALUES (
            :contractId,
            :engineNumber,
            :chassisNumber,
            :ownerSerialNo,
            :updatedBy,
            TRUE
        )
        """;

    private static final String UPDATE_CONTRACT_DETAIL_SQL = """
        UPDATE contract_details
        SET
            engine_number = :engineNumber,
            chassis_number = :chassisNumber,
            owner_serial_number = :ownerSerialNo,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP,
            is_active = TRUE
        WHERE contract_detail_id = :contractDetailId
        """;

    private static final String FIND_INSURANCE_ID_SQL = """
        SELECT asset_insurance_id
        FROM asset_insurances
        WHERE asset_id = :assetId
        ORDER BY asset_insurance_id
        LIMIT 1
        """;

    private static final String INSERT_INSURANCE_SQL = """
        INSERT INTO asset_insurances (
            asset_id,
            idv_amount,
            valid_to,
            insurance_company_code,
            premium_amount,
            updated_at
        )
        VALUES (
            :assetId,
            :idv,
            :insuranceExpiry,
            :insuranceCompany,
            :insurancePremium,
            CURRENT_TIMESTAMP
        )
        RETURNING asset_insurance_id
        """;

    private static final String UPDATE_INSURANCE_SQL = """
        UPDATE asset_insurances
        SET
            idv_amount = :idv,
            valid_to = :insuranceExpiry,
            insurance_company_code = :insuranceCompany,
            premium_amount = :insurancePremium,
            updated_at = CURRENT_TIMESTAMP
        WHERE asset_insurance_id = :assetInsuranceId
        """;

    private static final RowMapper<ContractAssetDraftDto> ROW_MAPPER = (rs, rowNum) -> new ContractAssetDraftDto(
        rs.getLong("contract_id"),
        rs.getLong("asset_id"),
        rs.getObject("asset_insurance_id", Long.class),
        rs.getString("asset_secured"),
        rs.getString("product_type"),
        rs.getString("vehicle_type_code"),
        rs.getString("deal_of_assets"),
        rs.getString("vehicle_make"),
        rs.getString("version"),
        rs.getString("manufacture_year"),
        rs.getString("owner_serial_no"),
        rs.getString("registration_number"),
        rs.getString("fuel_type"),
        rs.getBigDecimal("kms_run"),
        rs.getBigDecimal("market_value"),
        rs.getString("chassis_number"),
        rs.getString("engine_number"),
        rs.getBigDecimal("idv"),
        rs.getObject("insurance_expiry", java.time.LocalDate.class),
        rs.getString("insurance_company"),
        rs.getBigDecimal("insurance_premium"),
        rs.getString("property_type"),
        rs.getString("flat_no"),
        rs.getString("apartment_no"),
        rs.getString("street_name"),
        rs.getString("area_name"),
        rs.getString("property_city"),
        rs.getString("property_state"),
        rs.getBigDecimal("distance_from_office"),
        rs.getString("any_rent_received"),
        rs.getBigDecimal("rent_amount"),
        rs.getBigDecimal("guideline_value"),
        rs.getString("nature_of_business"),
        rs.getObject("date_of_incorporation", java.time.LocalDate.class),
        rs.getString("is_secured")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ContractAssetDraftRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<ContractAssetDraftDto> findByContractId(Long contractId) {
        List<ContractAssetDraftDto> rows = namedParameterJdbcTemplate.query(
            FIND_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public Long upsertAsset(ContractAssetDraftDto asset) {
        MapSqlParameterSource params = params(asset);
        if (asset.assetId() == null) {
            return namedParameterJdbcTemplate.queryForObject(INSERT_ASSET_SQL, params, Long.class);
        }

        namedParameterJdbcTemplate.update(UPDATE_ASSET_SQL, params);
        return asset.assetId();
    }

    public void updateContractSummary(ContractAssetDraftDto asset, String updatedBy) {
        namedParameterJdbcTemplate.update(
            UPDATE_CONTRACT_SUMMARY_SQL,
            params(asset).addValue("updatedBy", updatedBy)
        );
    }

    public void upsertContractDetail(ContractAssetDraftDto asset, String updatedBy) {
        MapSqlParameterSource lookupParams = new MapSqlParameterSource().addValue("contractId", asset.contractId());
        List<Long> detailIds = namedParameterJdbcTemplate.queryForList(
            FIND_CONTRACT_DETAIL_ID_SQL,
            lookupParams,
            Long.class
        );

        MapSqlParameterSource params = params(asset).addValue("updatedBy", updatedBy);
        if (detailIds.isEmpty()) {
            namedParameterJdbcTemplate.update(INSERT_CONTRACT_DETAIL_SQL, params);
            return;
        }

        namedParameterJdbcTemplate.update(
            UPDATE_CONTRACT_DETAIL_SQL,
            params.addValue("contractDetailId", detailIds.get(0))
        );
    }

    public Optional<Long> findInsuranceId(Long assetId) {
        List<Long> rows = namedParameterJdbcTemplate.queryForList(
            FIND_INSURANCE_ID_SQL,
            new MapSqlParameterSource().addValue("assetId", assetId),
            Long.class
        );
        return rows.stream().findFirst();
    }

    public Long upsertInsurance(Long assetId, Long assetInsuranceId, ContractAssetDraftDto asset) {
        MapSqlParameterSource params = params(asset)
            .addValue("assetId", assetId)
            .addValue("assetInsuranceId", assetInsuranceId);

        if (assetInsuranceId == null) {
            return namedParameterJdbcTemplate.queryForObject(INSERT_INSURANCE_SQL, params, Long.class);
        }

        namedParameterJdbcTemplate.update(UPDATE_INSURANCE_SQL, params);
        return assetInsuranceId;
    }

    private MapSqlParameterSource params(ContractAssetDraftDto asset) {
        return new MapSqlParameterSource()
            .addValue("contractId", asset.contractId())
            .addValue("assetId", asset.assetId())
            .addValue("assetSecured", clean(asset.assetSecured()))
            .addValue("productType", clean(asset.productType()))
            .addValue("vehicleTypeCode", clean(asset.vehicleTypeCode()))
            .addValue("dealOfAssets", clean(asset.dealOfAssets()))
            .addValue("vehicleMake", clean(asset.vehicleMake()))
            .addValue("version", clean(asset.version()))
            .addValue("manufactureYear", clean(asset.manufactureYear()))
            .addValue("ownerSerialNo", clean(asset.ownerSerialNo()))
            .addValue("registrationNumber", clean(asset.registrationNumber()))
            .addValue("fuelType", clean(asset.fuelType()))
            .addValue("kmsRun", asset.kmsRun())
            .addValue("marketValue", asset.marketValue())
            .addValue("chassisNumber", clean(asset.chassisNumber()))
            .addValue("engineNumber", clean(asset.engineNumber()))
            .addValue("idv", asset.idv())
            .addValue("insuranceExpiry", asset.insuranceExpiry())
            .addValue("insuranceCompany", clean(asset.insuranceCompany()))
            .addValue("insurancePremium", asset.insurancePremium())
            .addValue("propertyType", clean(asset.propertyType()))
            .addValue("flatNo", clean(asset.flatNo()))
            .addValue("apartmentNo", clean(asset.apartmentNo()))
            .addValue("streetName", clean(asset.streetName()))
            .addValue("areaName", clean(asset.areaName()))
            .addValue("propertyCity", clean(asset.propertyCity()))
            .addValue("propertyState", clean(asset.propertyState()))
            .addValue("distanceFromOffice", asset.distanceFromOffice())
            .addValue("anyRentReceived", clean(asset.anyRentReceived()))
            .addValue("rentAmount", asset.rentAmount())
            .addValue("guidelineValue", asset.guidelineValue())
            .addValue("natureOfBusiness", clean(asset.natureOfBusiness()))
            .addValue("dateOfIncorporation", asset.dateOfIncorporation())
            .addValue("isSecured", clean(asset.isSecured()));
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
