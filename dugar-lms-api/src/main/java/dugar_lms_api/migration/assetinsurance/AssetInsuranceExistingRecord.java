package dugar_lms_api.migration.assetinsurance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetInsuranceExistingRecord(
    Long assetInsuranceId,
    Long assetId,
    String insuranceCompanyCode,
    String policyNumber,
    String coverNoteNumber,
    LocalDate policyDate,
    LocalDate validFrom,
    LocalDate validTo,
    String policyBy,
    BigDecimal premiumAmount,
    String sourceRowHash
) {
}
