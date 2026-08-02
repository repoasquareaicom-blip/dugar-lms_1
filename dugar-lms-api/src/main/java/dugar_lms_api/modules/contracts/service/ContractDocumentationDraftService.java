package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.ContractDocumentationDraftDto;
import dugar_lms_api.modules.contracts.repository.ContractDocumentationDraftRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ContractDocumentationDraftService {

    private final ContractDocumentationDraftRepository contractDocumentationDraftRepository;

    public ContractDocumentationDraftService(ContractDocumentationDraftRepository contractDocumentationDraftRepository) {
        this.contractDocumentationDraftRepository = contractDocumentationDraftRepository;
    }

    public ContractDocumentationDraftDto getDocumentationDetails(Long contractId) {
        return contractDocumentationDraftRepository.findByContractId(contractId).orElse(null);
    }

    @Transactional
    public ContractDocumentationDraftDto saveDocumentationDetails(
        Long contractId,
        ContractDocumentationDraftDto request,
        Authentication authentication
    ) {
        ContractDocumentationDraftDto documentation = withContractId(contractId, request);
        String updatedBy = auditUser(authentication);
        contractDocumentationDraftRepository.updateContract(documentation, updatedBy);
        contractDocumentationDraftRepository.upsertContractDetail(documentation, updatedBy);
        contractDocumentationDraftRepository.replacePendingDocuments(contractId, documentation.pendingDocuments(), updatedBy);
        contractDocumentationDraftRepository.replaceUploads(contractId, documentation.documentUploads(), updatedBy);
        return contractDocumentationDraftRepository.findByContractId(contractId)
            .orElse(contractDocumentationDraftRepository.withChildren(documentation, List.of(), List.of()));
    }

    private ContractDocumentationDraftDto withContractId(Long contractId, ContractDocumentationDraftDto documentation) {
        return new ContractDocumentationDraftDto(
            contractId,
            documentation.documentType(),
            documentation.documentVerifiedBy(),
            documentation.documentsObtainedBy(),
            documentation.guarantorFiBy(),
            documentation.borrowerFiBy(),
            documentation.loanReferredBy(),
            documentation.tvrDoneBy(),
            documentation.vehicleByAgency(),
            documentation.vehicleInspectionBy(),
            documentation.propertyValuationBy(),
            documentation.legalOpinionBy(),
            documentation.branchCollectionToolBy(),
            documentation.documentsCheckedBy(),
            documentation.documentsVerifiedBy(),
            documentation.loanApprovedBy(),
            documentation.disbursedBy(),
            documentation.rcOnlineChecking(),
            documentation.hoCollectionToolBy(),
            documentation.areaCode(),
            documentation.hoTvrDoneBy(),
            documentation.stockMarkedToBank(),
            documentation.pendingDocuments(),
            documentation.documentUploads()
        );
    }

    private String auditUser(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) {
                return String.valueOf(userId);
            }
        }
        return authentication != null && authentication.getName() != null ? authentication.getName() : "system";
    }
}
