package dugar_lms_api.migration.contract;

public record ContractMigrationDuplicate(
    int excelRow,
    String contractType,
    String contractNumber,
    String reason
) {
}
