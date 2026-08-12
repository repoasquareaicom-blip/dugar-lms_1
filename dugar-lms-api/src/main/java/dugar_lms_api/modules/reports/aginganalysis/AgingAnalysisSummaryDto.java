package dugar_lms_api.modules.reports.aginganalysis;

import java.math.BigDecimal;

public record AgingAnalysisSummaryDto(
    long noOfAccounts,
    BigDecimal aum,
    BigDecimal current,
    BigDecimal totalOverdue,
    BigDecimal total
) {
}
