package dugar_lms_api.modules.wordtemplates.dto;

import java.util.List;

public record WordTemplateValidationDto(
    List<String> placeholders,
    List<String> recognized,
    List<String> unknown
) {
    public boolean valid() {
        return unknown == null || unknown.isEmpty();
    }
}
