package dugar_lms_api.modules.accounts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VoucherEditRequest(
    @NotBlank
    String editReason,
    @Valid
    @NotNull
    VoucherSaveRequest voucher
) {
}
