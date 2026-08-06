package dugar_lms_api.modules.reports.afc;

import java.math.BigDecimal;
import java.time.LocalDate;

record AfcReceipt(
    LocalDate receiptDate,
    String receiptNumber,
    BigDecimal amount
) {
}
