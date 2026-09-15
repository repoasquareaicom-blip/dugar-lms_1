package dugar_lms_api.modules.contracts.dto;

public record ContractFlagDto(
    Long contractFlagMasterId,
    String flagCode,
    String flagName
) {
}
