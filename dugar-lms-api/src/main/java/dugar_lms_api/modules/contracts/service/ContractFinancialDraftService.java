package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.ContractFinancialDraftDto;
import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import dugar_lms_api.modules.contracts.repository.ContractFinancialDraftRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ContractFinancialDraftService {

    private final ContractFinancialDraftRepository contractFinancialDraftRepository;
    private final ContractAccessRepository contractAccessRepository;

    public ContractFinancialDraftService(
        ContractFinancialDraftRepository contractFinancialDraftRepository,
        ContractAccessRepository contractAccessRepository
    ) {
        this.contractFinancialDraftRepository = contractFinancialDraftRepository;
        this.contractAccessRepository = contractAccessRepository;
    }

    public ContractFinancialDraftDto getFinancialDetails(Long contractId, Authentication authentication) {
        contractAccessRepository.requireAccess(contractId, ReportAccessScope.from(authentication));
        return contractFinancialDraftRepository.findByContractId(contractId).orElse(null);
    }

    @Transactional
    public ContractFinancialDraftDto saveFinancialDetails(
        Long contractId,
        ContractFinancialDraftDto request,
        Authentication authentication
    ) {
        contractAccessRepository.requireAccess(contractId, ReportAccessScope.from(authentication));
        ContractFinancialDraftDto financial = withContractId(contractId, request);
        validate(financial, contractFinancialDraftRepository.findContractDate(contractId));
        String updatedBy = auditUser(authentication);
        contractFinancialDraftRepository.updateContract(financial, updatedBy);
        contractFinancialDraftRepository.upsertContractDetail(financial, updatedBy);
        contractFinancialDraftRepository.replaceRepayments(contractId, financial.repaymentStructures());
        contractFinancialDraftRepository.recalculateContractIrr(contractId);
        return contractFinancialDraftRepository.findByContractId(contractId)
            .orElse(contractFinancialDraftRepository.withRepayments(financial, List.of()));
    }

    private void validate(ContractFinancialDraftDto financial, java.time.LocalDate contractDate) {
        if (financial.firstEmiDate() == null) {
            throw new IllegalArgumentException("First EMI Date is required.");
        }
        if (contractDate != null && financial.firstEmiDate().isBefore(contractDate)) {
            throw new IllegalArgumentException("First EMI Date must be greater than or equal to Contract Date.");
        }
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
            Boolean.TRUE.equals(financial.isFirstEmiPaid()),
            financial.firstEmiDate(),
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
