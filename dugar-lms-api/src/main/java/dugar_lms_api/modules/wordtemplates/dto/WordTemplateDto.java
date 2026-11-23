package dugar_lms_api.modules.wordtemplates.dto;

import java.time.LocalDateTime;

public record WordTemplateDto(
    Long id,
    String templateName,
    String originalFilename,
    Integer displayOrder,
    Boolean isActive,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy,
    WordTemplateValidationDto validation,
    String languageCode,
    String documentKey,
    Boolean hasFile
) {
    public WordTemplateDto(Long id,String templateName,String originalFilename,Integer displayOrder,
            Boolean isActive,LocalDateTime createdAt,String createdBy,LocalDateTime updatedAt,String updatedBy,
            WordTemplateValidationDto validation,String languageCode,String documentKey) {
        this(id,templateName,originalFilename,displayOrder,isActive,createdAt,createdBy,updatedAt,updatedBy,validation,languageCode,documentKey,false);
    }
}
