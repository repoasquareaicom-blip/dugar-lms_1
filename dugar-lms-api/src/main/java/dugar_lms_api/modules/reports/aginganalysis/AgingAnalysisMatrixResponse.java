package dugar_lms_api.modules.reports.aginganalysis;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AgingAnalysisMatrixResponse(
    LocalDate asOnDate,
    String reportName,
    List<AgingAnalysisMatrixRowDto> rows,
    List<String> warnings,
    LocalDateTime generatedAt,
    String generatedBy
) {
}
