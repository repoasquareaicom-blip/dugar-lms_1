package dugar_lms_api.migration.asset;

import java.math.BigDecimal;

record AssetMigrationRecord(
    Long contractId,
    String oracleContractType,
    String oracleContractNo,
    String financeType,
    String vehicleTypeCode,
    String registrationNumber,
    String normalizedRegistrationNumber,
    String engineNumber,
    String normalizedEngineNumber,
    String chassisNumber,
    String normalizedChassisNumber,
    String manufactureYear,
    BigDecimal equipmentValue,
    String securityOffered,
    String ownerSerialNo,
    String sourceTable,
    String sourceRowHash
) {
}
