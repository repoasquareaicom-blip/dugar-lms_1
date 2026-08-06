package dugar_lms_api.modules.reports.afc;

import java.time.LocalDate;

public record AfcReportRequest(
    String loanNumber,
    LocalDate asOnDate
) {
}
