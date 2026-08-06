package dugar_lms_api.modules.reports.demandlist;

import dugar_lms_api.common.pagination.PageResponse;

import java.time.LocalDateTime;
import java.util.List;

public record DemandListResponse(
    PageResponse<DemandListRowDto> rows,
    DemandListSummaryDto summary,
    List<String> warnings,
    LocalDateTime generatedAt,
    String generatedBy,
    boolean printLimitExceeded,
    String message
) {
}
