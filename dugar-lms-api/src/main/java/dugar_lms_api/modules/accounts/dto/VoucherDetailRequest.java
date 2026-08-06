package dugar_lms_api.modules.accounts.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record VoucherDetailRequest(
    @NotNull
    Integer serialNumber,
    String category,
    @NotBlank
    String ledgerCode,
    String ledgerName,
    String subLedgerCode,
    @DecimalMin(value = "0.00")
    BigDecimal debitAmount,
    @DecimalMin(value = "0.00")
    BigDecimal creditAmount,
    String partyCode,
    String partyName,
    String loanReference,
    String narration,
    String address
) {
}
