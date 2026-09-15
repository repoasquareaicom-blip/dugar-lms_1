package dugar_lms_api.modules.contracts.dto;

import java.util.List;

public record ContractFlagResponse(
    Long contractId,
    List<ContractFlagDto> flags
) {
}
