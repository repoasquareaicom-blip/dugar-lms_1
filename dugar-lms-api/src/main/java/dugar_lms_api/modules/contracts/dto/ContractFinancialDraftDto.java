package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ContractFinancialDraftDto(
    Long contractId,
    BigDecimal loanAmount,
    Integer tenureMonths,
    BigDecimal flatInterestRate,
    BigDecimal irrRate,
    BigDecimal insuranceDeposit,
    BigDecimal totalContractValue,
    String repaymentTerms,
    Boolean isFirstEmiPaid,
    LocalDate firstEmiDate,
    Integer moratoriumMonths,
    String repaymentType,
    BigDecimal emiAdvance,
    BigDecimal processingCharges,
    BigDecimal rtoCharges,
    BigDecimal valuationCharges,
    BigDecimal stampDuty,
    BigDecimal rcHoldingAmount,
    BigDecimal otherCharges,
    String modeOfPayment,
    String paymentDoneTo,
    String payee1,
    String payee2,
    String payee3,
    List<ContractRepaymentStructureDto> repaymentStructures
) {
}
