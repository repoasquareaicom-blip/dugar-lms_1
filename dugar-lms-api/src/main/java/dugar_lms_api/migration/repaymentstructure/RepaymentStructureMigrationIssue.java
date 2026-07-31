package dugar_lms_api.migration.repaymentstructure;

public record RepaymentStructureMigrationIssue(
    Integer excelRow,
    String referenceKey,
    String type,
    String reason
) {
}
