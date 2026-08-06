package dugar_lms_api.modules.accounts.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record LedgerCodeRequest(
    @NotBlank
    String ledgerCode,
    @NotBlank
    String ledgerName,
    BigDecimal openingBalance,
    Boolean isActive
) {
}
