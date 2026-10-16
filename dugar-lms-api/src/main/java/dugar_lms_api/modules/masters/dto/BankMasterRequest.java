package dugar_lms_api.modules.masters.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankMasterRequest(
    @NotBlank
    @Size(max = 30)
    String bankCode,
    @NotBlank
    @Size(max = 150)
    String bankName,
    Boolean isActive
) {
}
