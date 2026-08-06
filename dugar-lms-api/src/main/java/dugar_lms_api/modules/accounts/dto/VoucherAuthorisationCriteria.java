package dugar_lms_api.modules.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VoucherAuthorisationCriteria(
    String keyword,
    String voucherType,
    LocalDate voucherDateFrom,
    LocalDate voucherDateTo,
    BigDecimal minimumAmount,
    BigDecimal maximumAmount,
    Long submittedBy,
    Integer page,
    Integer size,
    String sortColumn,
    String sortDirection
) {
}
