package dugar_lms_api.modules.contracts.dto;

public record ContractDocumentUploadDto(
    Long documentUploadId,
    String documentCategory,
    String fileName,
    Long fileSize,
    String contentType,
    String storagePath
) {
}
