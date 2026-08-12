package dugar_lms_api.modules.reports.aginganalysis;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AgingAnalysisEmiDto(
    Integer emiNumber,
    LocalDate dueDate,
    BigDecimal installmentAmount,
    BigDecimal paidAmount,
    BigDecimal outstandingAmount,
    String status
) {
}
