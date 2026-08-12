package dugar_lms_api.modules.reports.aginganalysis;

import java.math.BigDecimal;

public record AgingAnalysisMatrixRowDto(
    String label,
    BigDecimal principalOutstanding,
    BigDecimal standard,
    BigDecimal par0To30,
    BigDecimal par31To60,
    BigDecimal par61To90,
    BigDecimal par91To180,
    BigDecimal par181To365,
    BigDecimal parAbove365
) {
}
