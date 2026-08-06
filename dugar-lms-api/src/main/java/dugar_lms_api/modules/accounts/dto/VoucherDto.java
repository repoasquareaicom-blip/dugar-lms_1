package dugar_lms_api.modules.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VoucherDto(
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
    List<VoucherDetailDto> details
) {
}
