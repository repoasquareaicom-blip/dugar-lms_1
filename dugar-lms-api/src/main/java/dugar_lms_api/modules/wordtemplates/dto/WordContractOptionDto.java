package dugar_lms_api.modules.wordtemplates.dto;

public record WordContractOptionDto(
    Long contractId,
    String contractNumber,
    String borrowerName,
    String borrowerCode,
    String displayName
) {
}
