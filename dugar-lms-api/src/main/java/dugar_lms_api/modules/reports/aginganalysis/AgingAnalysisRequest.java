package dugar_lms_api.modules.reports.aginganalysis;

import java.time.LocalDate;

public record AgingAnalysisRequest(
    LocalDate asOnDate,
    String areaCode,
    String contractNumber
) {
}
