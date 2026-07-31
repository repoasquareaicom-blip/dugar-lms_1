package dugar_lms_api.migration.common;

import java.time.LocalDateTime;

public record MigrationRun(
    Long migrationRunId,
    String migrationType,
    String fileName,
    String sheetName,
    int totalRows,
    int insertedCount,
    int duplicateCount,
    int failedCount,
    String status,
    String uploadedBy,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt
) {
}
