package dugar_lms_api.modules.assets.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssetDto(
    Long assetId,
    Long contractId,
    String oracleContractType,
    String oracleContractNo,
    String financeType,
    String vehicleTypeCode,
    String registrationNumber,
    String engineNumber,
    String chassisNumber,
    String manufactureYear,
    BigDecimal equipmentValue,
    String securityOffered,
    String ownerSerialNo,
    String sourceTable,
    String sourceRowHash,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
