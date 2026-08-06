package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractAssetDraftDto(
    Long contractId,
    Long assetId,
    Long assetInsuranceId,
    String assetSecured,
    String productType,
    String vehicleTypeCode,
    String dealOfAssets,
    String vehicleMake,
    String version,
    String manufactureYear,
    String ownerSerialNo,
    String registrationNumber,
    String fuelType,
    BigDecimal kmsRun,
    BigDecimal marketValue,
    String chassisNumber,
    String engineNumber,
    BigDecimal idv,
    LocalDate insuranceExpiry,
    String insuranceCompany,
    BigDecimal insurancePremium,
    String propertyType,
    String flatNo,
    String apartmentNo,
    String streetName,
    String areaName,
    String propertyCity,
    String propertyState,
    BigDecimal distanceFromOffice,
    String anyRentReceived,
    BigDecimal rentAmount,
    BigDecimal guidelineValue,
    String natureOfBusiness,
    LocalDate dateOfIncorporation,
    String isSecured,
    String proposalCategory,
    String riskLevel
) {
}
