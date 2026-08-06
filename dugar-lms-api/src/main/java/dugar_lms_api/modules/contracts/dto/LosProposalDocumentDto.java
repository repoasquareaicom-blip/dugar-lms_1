package dugar_lms_api.modules.contracts.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDateTime;

public record LosProposalDocumentDto(
    Long id,
    @JsonAlias("proposal_id")
    Long proposalId,
    @JsonAlias("document_type")
    String documentType,
    String category,
    @JsonAlias("file_path")
    String filePath,
    @JsonAlias("uploaded_at")
    LocalDateTime uploadedAt,
    @JsonAlias("category_id")
    Long categoryId,
    @JsonAlias("created_by")
    Long createdBy,
    @JsonAlias("is_locked_for_agent")
    Integer isLockedForAgent
) {
}
