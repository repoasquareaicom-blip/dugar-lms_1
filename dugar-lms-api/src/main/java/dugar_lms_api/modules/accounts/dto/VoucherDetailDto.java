package dugar_lms_api.modules.accounts.dto;

import java.math.BigDecimal;

public record VoucherDetailDto(
    Long voucherDetailId,
    Integer serialNumber,
    String category,
    String ledgerCode,
    String ledgerName,
    BigDecimal debitAmount,
    BigDecimal creditAmount,
    String partyCode,
    String partyName,
    String loanReference,
    String narration,
    String address
) {
}
