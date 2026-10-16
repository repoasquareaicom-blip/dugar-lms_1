package dugar_lms_api.modules.wordtemplates.dto;

public record WordPlaceholderFieldDto(
    String dataSource,
    String category,
    String fieldName,
    String placeholderCode,
    String sourceTable,
    String sourceColumn,
    boolean calculated,
    boolean repeating
) {
}
