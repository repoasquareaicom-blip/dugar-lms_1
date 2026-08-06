package dugar_lms_api.modules.accounts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VoucherSaveRequest(
    @NotBlank
    String voucherType,
    String voucherTypeDescription,
    String voucherNumber,
    @NotNull
    LocalDate voucherDate,
    @NotNull
    LocalDate systemDate,
    String transactionType,
    @NotNull
    @DecimalMin(value = "0.01")
    BigDecimal voucherAmount,
    String category,
    Long contractId,
    String contractNumber,
    String contractType,
    String bankCode,
    String headerControlCode,
    String headerControlName,
    String remarks,
    Boolean allowDuplicateDetails,
    @Valid
    @NotEmpty
    List<VoucherDetailRequest> details
) {
}
