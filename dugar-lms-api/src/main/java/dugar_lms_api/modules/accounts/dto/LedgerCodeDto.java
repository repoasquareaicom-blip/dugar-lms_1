package dugar_lms_api.modules.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LedgerCodeDto(
    Long ledgerId,
    String ledgerCode,
    String ledgerName,
    BigDecimal openingBalance,
    String accountType,
    String scheduleDebitSubCode,
    String scheduleCreditSubCode,
    String trialBalanceMajorCode,
    String trialBalanceMinorCode,
    String plBsFlag,
    Boolean hasSubLedger,
    Boolean linkReference,
    String legacyUserId,
    LocalDate legacyUserDoc,
    String sourceSystem,
    Boolean isActive,
    Long createdBy,
    LocalDateTime createdAt,
    Long updatedBy,
    LocalDateTime updatedAt
) {
}
