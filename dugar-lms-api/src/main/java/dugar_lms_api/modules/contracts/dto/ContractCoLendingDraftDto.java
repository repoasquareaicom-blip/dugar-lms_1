package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;

public record ContractCoLendingDraftDto(
    Long contractId,
    String coLendingType,
    String coLenderName,
    BigDecimal securityDepositAmount,
    BigDecimal contributionSharePercent,
    BigDecimal emiSharePercent,
    BigDecimal revenueSharePercent,
    BigDecimal riskSharePercent
) {
}
