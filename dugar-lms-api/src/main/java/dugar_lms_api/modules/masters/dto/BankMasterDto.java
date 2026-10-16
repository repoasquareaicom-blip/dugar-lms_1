package dugar_lms_api.modules.masters.dto;

import java.time.LocalDateTime;

public record BankMasterDto(
    Long id,
    String bankCode,
    String bankName,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
