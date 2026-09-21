package dugar_lms_api.modules.reports.demandlist;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContractFollowUpDto(
    Long contractFollowUpId,
    Long contractId,
    String commentText,
    String followUpType,
    LocalDate followUpDate,
    Long createdBy,
    String createdByUsername,
    LocalDateTime createdAt
) {
}
