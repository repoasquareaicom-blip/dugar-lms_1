package dugar_lms_api.modules.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VoucherSummaryDto(
    Long voucherHeaderId,
    String voucherType,
    String voucherNumber,
    LocalDate voucherDate,
    String transactionType,
    BigDecimal voucherAmount,
    String contractNumber,
    String headerControlCode,
    String headerControlName,
    String status,
    Integer versionNumber,
    Long submittedBy,
    java.time.LocalDateTime submittedAt,
    int detailCount
) {
}
