package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.LosContractReceiveRequest;
import dugar_lms_api.modules.contracts.dto.LosContractReceiveResponse;
import dugar_lms_api.modules.contracts.repository.LosContractReceiveRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class LosContractReceiveService {

    private final LosContractReceiveRepository losContractReceiveRepository;

    public LosContractReceiveService(LosContractReceiveRepository losContractReceiveRepository) {
        this.losContractReceiveRepository = losContractReceiveRepository;
    }

    @Transactional
    public LosContractReceiveResponse receive(LosContractReceiveRequest request, Authentication authentication) {
        if (request == null || request.id() == null) {
            throw new IllegalArgumentException("LOS proposal id is required");
        }
        if (isBlank(request.borrowerName())) {
            throw new IllegalArgumentException("borrower_name is required");
        }

        String updatedBy = auditUser(authentication);
        String borrowerCode = "LOSB" + String.format("%06d", request.id());
        String coApplicantCode = isBlank(request.coApplicantName()) ? null : "LOSC" + String.format("%06d", request.id());

        losContractReceiveRepository.upsertBorrower(borrowerCode, request, updatedBy);
        if (coApplicantCode != null) {
            losContractReceiveRepository.upsertCoApplicant(coApplicantCode, request, updatedBy);
        }

        Long contractId = losContractReceiveRepository.findContractIdByLosProposalId(request.id()).orElse(null);
        boolean created = contractId == null;
        if (created) {
            contractId = losContractReceiveRepository.insertContract(request, borrowerCode, coApplicantCode, updatedBy);
        } else {
            losContractReceiveRepository.updateContract(contractId, request, borrowerCode, coApplicantCode, updatedBy);
        }

        losContractReceiveRepository.upsertContractDetail(contractId, request, updatedBy);
        losContractReceiveRepository.upsertAsset(contractId, request);
        losContractReceiveRepository.replaceRepayments(contractId, request);
        losContractReceiveRepository.replaceDocuments(contractId, request.documents(), updatedBy);

        return new LosContractReceiveResponse(
            contractId,
            losContractReceiveRepository.findContractNumber(contractId),
            request.id(),
            created,
            "DRAFT",
            true
        );
    }

    private String auditUser(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) {
                return String.valueOf(userId);
            }
        }
        return authentication != null && authentication.getName() != null ? authentication.getName() : "los";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
