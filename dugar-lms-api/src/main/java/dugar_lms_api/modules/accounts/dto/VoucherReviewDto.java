package dugar_lms_api.modules.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record VoucherReviewDto(
    Long voucherHeaderId,
    String voucherType,
    String voucherTypeDescription,
    String voucherNumber,
    LocalDate voucherDate,
    LocalDate systemDate,
    String transactionType,
    BigDecimal voucherAmount,
    String contractNumber,
    Long contractId,
    String headerControlCode,
    String headerControlName,
    String remarks,
    String status,
    Integer versionNumber,
    Long createdBy,
    Long submittedBy,
    LocalDateTime submittedAt,
    Long authorisedBy,
    LocalDateTime authorisedAt,
    Long reopenedBy,
    LocalDateTime reopenedAt,
    Long rejectedBy,
    LocalDateTime rejectedAt,
    String rejectionReason,
    Long cancelledBy,
    LocalDateTime cancelledAt,
    String cancellationReason,
    BigDecimal totalDebit,
    BigDecimal totalCredit,
    List<VoucherDetailDto> details,
    List<VoucherHistoryDto> history
) {
}
