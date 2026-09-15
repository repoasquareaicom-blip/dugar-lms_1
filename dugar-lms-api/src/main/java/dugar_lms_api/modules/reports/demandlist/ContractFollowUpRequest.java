package dugar_lms_api.modules.reports.demandlist;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ContractFollowUpRequest(
    @NotBlank
    String commentText,
    LocalDate followUpDate
) {
}
