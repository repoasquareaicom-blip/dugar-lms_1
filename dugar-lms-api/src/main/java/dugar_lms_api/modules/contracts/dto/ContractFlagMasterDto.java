package dugar_lms_api.modules.contracts.dto;

public record ContractFlagMasterDto(
    Long contractFlagMasterId,
    String flagCode,
    String flagName,
    Integer displayOrder
) {
}
