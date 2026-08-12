package dugar_lms_api.modules.reports.aginganalysis;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AgingAnalysisResponse(
    LocalDate asOnDate,
    List<AgingAnalysisBranchRowDto> branchWise,
    List<AgingAnalysisConsolidatedRowDto> consolidated,
    AgingAnalysisSummaryDto summary,
    List<String> warnings,
    LocalDateTime generatedAt,
    String generatedBy
) {
}
