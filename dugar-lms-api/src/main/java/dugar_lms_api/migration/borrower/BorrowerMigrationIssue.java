package dugar_lms_api.migration.borrower;

public record BorrowerMigrationIssue(
    Integer excelRow,
    String partyCode,
    String type,
    String reason
) {
}
