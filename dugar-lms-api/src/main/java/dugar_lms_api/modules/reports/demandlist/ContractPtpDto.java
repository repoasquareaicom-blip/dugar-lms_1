package dugar_lms_api.modules.reports.demandlist;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContractPtpDto(
    Long contractPtpId,
    Long contractId,
    LocalDate ptpDate,
    Long createdBy,
    String createdByUsername,
    LocalDateTime createdAt
) {
}
