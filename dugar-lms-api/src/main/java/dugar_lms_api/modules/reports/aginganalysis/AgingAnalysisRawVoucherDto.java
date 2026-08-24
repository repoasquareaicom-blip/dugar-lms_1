package dugar_lms_api.modules.reports.aginganalysis;

import java.util.List;
import java.util.Map;

public record AgingAnalysisRawVoucherDto(
    List<Map<String, Object>> headers,
    List<Map<String, Object>> details
) {
}
