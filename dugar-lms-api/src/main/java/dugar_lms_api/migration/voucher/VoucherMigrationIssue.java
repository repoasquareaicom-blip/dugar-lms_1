package dugar_lms_api.migration.voucher;

public record VoucherMigrationIssue(
    Integer excelRow,
    String referenceKey,
    String type,
    String reason
) {
}
