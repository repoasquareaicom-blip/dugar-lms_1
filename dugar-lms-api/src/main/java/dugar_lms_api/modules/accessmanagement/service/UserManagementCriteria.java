package dugar_lms_api.modules.accessmanagement.service;

public record UserManagementCriteria(
    String keyword,
    Boolean isActive,
    Integer page,
    Integer size,
    String sortColumn,
    String sortDirection
) {
}
