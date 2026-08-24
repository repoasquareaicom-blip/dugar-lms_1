package dugar_lms_api.modules.reports.aginganalysis;

import java.math.BigDecimal;

public record ConsolidatedPortfolioRowDto(
    Integer sortOrder,
    String label,
    Long noOfAccounts,
    BigDecimal principalOutstandingCr,
    BigDecimal standardCr,
    BigDecimal days0To30Cr,
    BigDecimal days31To60Cr,
    BigDecimal days61To90Cr,
    BigDecimal days91To180Cr,
    BigDecimal days181To365Cr,
    BigDecimal daysAbove365Cr
) {
}
