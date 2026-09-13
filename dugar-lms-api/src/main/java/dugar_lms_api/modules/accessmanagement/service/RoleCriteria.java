package dugar_lms_api.modules.accessmanagement.service;

public record RoleCriteria(
    String keyword,
    Boolean isActive,
    Integer page,
    Integer size,
    String sortColumn,
    String sortDirection
) {
}
