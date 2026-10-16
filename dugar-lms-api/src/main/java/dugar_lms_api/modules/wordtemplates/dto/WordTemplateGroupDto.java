package dugar_lms_api.modules.wordtemplates.dto;

import java.time.LocalDateTime;

public record WordTemplateGroupDto(
    Long id,
    String groupName,
    String description,
    String dataSource,
    Integer displayOrder,
    Boolean isActive,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy,
    Integer templateCount
) {
}
