package dugar_lms_api.modules.contracts.dto;

import java.util.List;

public record ContractPartyDraftResponse(
    Long contractId,
    String contractNumber,
    boolean contractCreated,
    List<PartyDraftSaveResult> parties
) {
}
