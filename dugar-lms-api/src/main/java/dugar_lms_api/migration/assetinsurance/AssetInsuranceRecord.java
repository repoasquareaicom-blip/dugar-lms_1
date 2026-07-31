package dugar_lms_api.migration.assetinsurance;

import java.math.BigDecimal;
import java.time.LocalDate;

record AssetInsuranceRecord(
    Long assetId,
    String insuranceCompanyCode,
    String policyNumber,
    String normalizedPolicyNumber,
    String coverNoteNumber,
    LocalDate policyDate,
    LocalDate validFrom,
    LocalDate validTo,
    String policyBy,
    BigDecimal premiumAmount,
    String sourceRowHash
) {
}
