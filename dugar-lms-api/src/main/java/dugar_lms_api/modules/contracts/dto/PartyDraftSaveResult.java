package dugar_lms_api.modules.contracts.dto;

public record PartyDraftSaveResult(
    String role,
    String partyCode,
    String partyType,
    boolean created
) {
}
