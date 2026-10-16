package dugar_lms_api.modules.accessmanagement.dto;

public record MenuAccessResponse(
    String menuCode,
    boolean canView
) {
}
