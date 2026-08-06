package dugar_lms_api.modules.reports.afc;

import java.time.LocalDateTime;
import java.util.List;

public record AfcReportResponse(
    AfcReportHeaderDto header,
    List<AfcReportRowDto> rows,
    List<String> warnings,
    LocalDateTime generatedAt,
    String generatedBy
) {
}
