package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.ContractCoLendingDraftDto;
import dugar_lms_api.modules.contracts.repository.ContractCoLendingDraftRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ContractCoLendingDraftService {

    private final ContractCoLendingDraftRepository contractCoLendingDraftRepository;

    public ContractCoLendingDraftService(ContractCoLendingDraftRepository contractCoLendingDraftRepository) {
        this.contractCoLendingDraftRepository = contractCoLendingDraftRepository;
    }

    public ContractCoLendingDraftDto getCoLendingDetails(Long contractId) {
        return contractCoLendingDraftRepository.findByContractId(contractId).orElse(null);
    }

    @Transactional
    public ContractCoLendingDraftDto saveCoLendingDetails(
        Long contractId,
        ContractCoLendingDraftDto request,
        Authentication authentication
    ) {
        ContractCoLendingDraftDto coLending = withContractId(contractId, request);
        contractCoLendingDraftRepository.upsertContractDetail(coLending, auditUser(authentication));
        return contractCoLendingDraftRepository.findByContractId(contractId).orElse(coLending);
    }

    private ContractCoLendingDraftDto withContractId(Long contractId, ContractCoLendingDraftDto coLending) {
        return new ContractCoLendingDraftDto(
            contractId,
            coLending.coLendingType(),
            coLending.coLenderName(),
            coLending.securityDepositAmount(),
            coLending.contributionSharePercent(),
            coLending.emiSharePercent(),
            coLending.revenueSharePercent(),
            coLending.riskSharePercent()
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
