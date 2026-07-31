package dugar_lms_api.migration.assetinsurance;

public record AssetInsuranceSourceRow(
    int excelRow,
    String contractType,
    String contractNumber,
    String insuranceCompanyCode,
    String policyNumber,
    String coverNoteNumber,
    String policyDate,
    String validFrom,
    String validTo,
    String policyBy,
    String premiumAmount
) {

    boolean isBlank() {
        return isBlank(contractType)
            && isBlank(contractNumber)
            && isBlank(insuranceCompanyCode)
            && isBlank(policyNumber)
            && isBlank(coverNoteNumber)
            && isBlank(policyDate)
            && isBlank(validFrom)
            && isBlank(validTo)
            && isBlank(policyBy)
            && isBlank(premiumAmount);
    }

    boolean isRepeatedHeader() {
        return "CONT_TYPE".equalsIgnoreCase(trim(contractType))
            || "CONT_NO".equalsIgnoreCase(trim(contractNumber));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
