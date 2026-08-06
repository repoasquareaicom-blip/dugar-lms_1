package dugar_lms_api.modules.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VoucherSaveResponse(
    Long voucherHeaderId,
    String voucherType,
    String voucherNumber,
    LocalDate voucherDate,
    BigDecimal voucherAmount,
    int detailCount
) {
}
