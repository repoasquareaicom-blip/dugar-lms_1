package dugar_lms_api.modules.masters.service;

public record BankMasterCriteria(
    String keyword,
    Boolean isActive,
    Integer page,
    Integer size,
    String sortColumn,
    String sortDirection
) {
}
