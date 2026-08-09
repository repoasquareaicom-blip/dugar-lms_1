package dugar_lms_api.modules.contracts.dto;

import java.time.LocalDate;

public record ContractHeaderDraftDto(
    Long contractId,
    String contractNumber,
    LocalDate contractDate
) {
}
