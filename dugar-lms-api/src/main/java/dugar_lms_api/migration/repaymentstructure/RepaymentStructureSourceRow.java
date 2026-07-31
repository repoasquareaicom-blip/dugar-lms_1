package dugar_lms_api.migration.repaymentstructure;

public record RepaymentStructureSourceRow(
    int excelRow,
    String contractType,
    String contractNumber,
    String sequenceNo,
    String numberOfInstallments,
    String installmentAmount
) {

    boolean isBlank() {
        return isBlank(contractType)
            && isBlank(contractNumber)
            && isBlank(sequenceNo)
            && isBlank(numberOfInstallments)
            && isBlank(installmentAmount);
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
