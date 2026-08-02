package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.ContractFinancialDraftDto;
import dugar_lms_api.modules.contracts.repository.ContractFinancialDraftRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ContractFinancialDraftService {

    private final ContractFinancialDraftRepository contractFinancialDraftRepository;

    public ContractFinancialDraftService(ContractFinancialDraftRepository contractFinancialDraftRepository) {
        this.contractFinancialDraftRepository = contractFinancialDraftRepository;
    }

    public ContractFinancialDraftDto getFinancialDetails(Long contractId) {
        return contractFinancialDraftRepository.findByContractId(contractId).orElse(null);
    }

    @Transactional
    public ContractFinancialDraftDto saveFinancialDetails(
        Long contractId,
        ContractFinancialDraftDto request,
        Authentication authentication
    ) {
        ContractFinancialDraftDto financial = withContractId(contractId, request);
        String updatedBy = auditUser(authentication);
        contractFinancialDraftRepository.updateContract(financial, updatedBy);
        contractFinancialDraftRepository.upsertContractDetail(financial, updatedBy);
        contractFinancialDraftRepository.replaceRepayments(contractId, financial.repaymentStructures());
        return contractFinancialDraftRepository.findByContractId(contractId)
            .orElse(contractFinancialDraftRepository.withRepayments(financial, List.of()));
    }

    private ContractFinancialDraftDto withContractId(Long contractId, ContractFinancialDraftDto financial) {
        return new ContractFinancialDraftDto(
            contractId,
            financial.loanAmount(),
            financial.tenureMonths(),
            financial.flatInterestRate(),
            financial.irrRate(),
            financial.insuranceDeposit(),
            financial.totalContractValue(),
            financial.repaymentTerms(),
            financial.moratoriumMonths(),
            financial.repaymentType(),
            financial.emiAdvance(),
            financial.processingCharges(),
            financial.rtoCharges(),
            financial.valuationCharges(),
            financial.stampDuty(),
            financial.rcHoldingAmount(),
            financial.otherCharges(),
            financial.modeOfPayment(),
            financial.paymentDoneTo(),
            financial.payee1(),
            financial.payee2(),
            financial.payee3(),
            financial.repaymentStructures()
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
