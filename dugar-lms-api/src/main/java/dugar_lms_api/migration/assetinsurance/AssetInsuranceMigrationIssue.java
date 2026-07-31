package dugar_lms_api.migration.assetinsurance;

public record AssetInsuranceMigrationIssue(
    Integer excelRow,
    String referenceKey,
    String type,
    String reason
) {
}
