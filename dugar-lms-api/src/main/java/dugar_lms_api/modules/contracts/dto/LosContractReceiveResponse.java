package dugar_lms_api.modules.contracts.dto;

public record LosContractReceiveResponse(
    Long contractId,
    String contractNumber,
    Long losProposalId,
    boolean created,
    String status,
    boolean losReceived
) {
}
