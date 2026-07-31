package dugar_lms_api.migration.contract;

public record ContractMigrationError(
    int excelRow,
    String contractType,
    String contractNumber,
    String reason
) {
}
