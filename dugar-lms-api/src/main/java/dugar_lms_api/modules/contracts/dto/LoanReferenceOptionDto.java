package dugar_lms_api.modules.contracts.dto;

public record LoanReferenceOptionDto(
    Long contractId,
    String contractNumber,
    String legacyContractNumber,
    String product,
    String contractType,
    String branch,
    String areaCode,
    String customerCode,
    String borrowerCode,
    String customerName,
    String borrowerName,
    String customerAddress,
    String customerCity,
    String customerState,
    String customerPinCode
) {
}
