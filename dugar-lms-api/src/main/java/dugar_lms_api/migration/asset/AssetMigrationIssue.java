package dugar_lms_api.migration.asset;

public record AssetMigrationIssue(
    Integer excelRow,
    String referenceKey,
    String type,
    String reason
) {
}
