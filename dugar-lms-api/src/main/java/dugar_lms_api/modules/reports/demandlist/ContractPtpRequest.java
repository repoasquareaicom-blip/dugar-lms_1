package dugar_lms_api.modules.reports.demandlist;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ContractPtpRequest(
    @NotNull(message = "PTP date is required.")
    LocalDate ptpDate
) {
}
