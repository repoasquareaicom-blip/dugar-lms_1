package dugar_lms_api.modules.accounts.dto;

import java.time.LocalDateTime;

public record VoucherHistoryDto(
    Long voucherHeaderHistoryId,
    Long voucherHeaderId,
    Integer versionNumber,
    String previousStatus,
    Long changedBy,
    LocalDateTime changedAt,
    String changeReason
) {
}
