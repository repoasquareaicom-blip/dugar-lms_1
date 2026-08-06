package dugar_lms_api.modules.accounts.service;

public record LedgerCodeCriteria(
    String keyword,
    Boolean isActive,
    Integer page,
    Integer size,
    String sortColumn,
    String sortDirection
) {
}
