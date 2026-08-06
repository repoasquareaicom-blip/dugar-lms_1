package dugar_lms_api.modules.reports.afc;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AfcReportRowDto(
    Integer serialNumber,
    LocalDate dueDate,
    LocalDate whenPaid,
    BigDecimal dueAmount,
    BigDecimal emiAmountPaid,
    String receiptNumber,
    Integer delayDays,
    BigDecimal afcAmount,
    BigDecimal contractBalance,
    BigDecimal currentAccountBalance
) {
}
