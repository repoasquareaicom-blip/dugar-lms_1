package dugar_lms_api.migration.common;

import java.time.LocalDateTime;

public record MigrationRunDetail(
    Long migrationRunDetailId,
    Long migrationRunId,
    Integer excelRow,
    String referenceKey,
    String resultType,
    String reason,
    String sourceData,
    LocalDateTime createdAt
) {
}
