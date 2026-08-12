package dugar_lms_api.modules.reports.aginganalysis;

import java.math.BigDecimal;

public record AgingAnalysisConsolidatedRowDto(
    String bucket,
    String label,
    Long noOfAccounts,
    BigDecimal principalOutstanding,
    BigDecimal interestOutstanding,
    BigDecimal total,
    BigDecimal portfolioPercent
) {
}
