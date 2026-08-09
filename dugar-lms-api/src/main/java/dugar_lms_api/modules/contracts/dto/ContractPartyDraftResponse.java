package dugar_lms_api.modules.contracts.dto;

import java.time.LocalDate;
import java.util.List;

public record ContractPartyDraftResponse(
    Long contractId,
    String contractNumber,
    LocalDate contractDate,
    boolean contractCreated,
    List<PartyDraftSaveResult> parties
) {
}
