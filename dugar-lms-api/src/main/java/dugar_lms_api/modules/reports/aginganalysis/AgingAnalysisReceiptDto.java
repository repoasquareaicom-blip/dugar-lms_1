package dugar_lms_api.modules.reports.aginganalysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AgingAnalysisReceiptDto(
    LocalDate voucherDate,
    String voucherNumber,
    String voucherType,
    String receiptNumber,
    String temporaryReceiptNumber,
    String ledgerCode,
    String subLedgerCode,
    BigDecimal debitAmount,
    BigDecimal creditAmount,
    BigDecimal collectionAmount,
    String narration,
    LocalDateTime authorisedAt
) {
}
