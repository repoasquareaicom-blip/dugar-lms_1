package dugar_lms_api.modules.reports.aginganalysis;

import java.math.BigDecimal;

public record AgingAnalysisBranchRowDto(
    String areaCode,
    String areaName,
    long noOfAccounts,
    BigDecimal aum,
    BigDecimal current,
    BigDecimal bucket1To30,
    BigDecimal bucket31To60,
    BigDecimal bucket61To90,
    BigDecimal bucket91To120,
    BigDecimal bucket121To150,
    BigDecimal bucket151To180,
    BigDecimal bucketAbove180,
    BigDecimal total,
    BigDecimal interestOutstanding
) {
}
