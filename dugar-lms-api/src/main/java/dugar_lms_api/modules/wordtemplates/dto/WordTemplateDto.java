package dugar_lms_api.modules.wordtemplates.dto;

import java.time.LocalDateTime;

public record WordTemplateDto(
    Long id,
    Long groupId,
    String groupName,
    String templateName,
    String originalFilename,
    Integer displayOrder,
    Boolean isActive,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy,
    WordTemplateValidationDto validation
) {
}
