package dugar_lms_api.modules.contracts.dto;

import java.util.List;

public record ContractDocumentationDraftDto(
    Long contractId,
    String documentType,
    String documentVerifiedBy,
    String documentsObtainedBy,
    String guarantorFiBy,
    String borrowerFiBy,
    String loanReferredBy,
    String tvrDoneBy,
    String vehicleByAgency,
    String vehicleInspectionBy,
    String propertyValuationBy,
    String legalOpinionBy,
    String branchCollectionToolBy,
    String documentsCheckedBy,
    String documentsVerifiedBy,
    String loanApprovedBy,
    String disbursedBy,
    String rcOnlineChecking,
    String hoCollectionToolBy,
    String areaCode,
    String hoTvrDoneBy,
    String stockMarkedToBank,
    List<ContractPendingDocumentDto> pendingDocuments,
    List<ContractDocumentUploadDto> documentUploads
) {
}
