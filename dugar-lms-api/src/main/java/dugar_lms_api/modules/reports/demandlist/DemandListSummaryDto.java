package dugar_lms_api.modules.reports.demandlist;

import java.math.BigDecimal;

public record DemandListSummaryDto(
    long contractCount,
    BigDecimal totalContractValue,
    BigDecimal totalAuthorisedReceipts,
    BigDecimal principalOutstanding,
    BigDecimal interestOutstanding,
    BigDecimal totalOutstanding,
    BigDecimal overdueAmount,
    BigDecimal currentDue
) {
    public static DemandListSummaryDto zero() {
        return new DemandListSummaryDto(
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }
}
