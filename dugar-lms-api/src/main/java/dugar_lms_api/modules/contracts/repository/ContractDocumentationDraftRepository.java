package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.ContractDocumentUploadDto;
import dugar_lms_api.modules.contracts.dto.ContractDocumentationDraftDto;
import dugar_lms_api.modules.contracts.dto.ContractPendingDocumentDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ContractDocumentationDraftRepository {

    private static final String FIND_DOCUMENTATION_SQL = """
        SELECT
            c.contract_id,
            cd.document_type,
            cd.document_verified_by,
            cd.documents_obtained_by,
            cd.guarantor_fi_by,
            cd.borrower_fi_by,
            cd.loan_referred_by,
            cd.tvr_done_by,
            cd.vehicle_by_agency,
            cd.vehicle_inspection_by,
            cd.property_valuation_by,
            cd.legal_opinion_by,
            cd.branch_collection_tool_by,
            cd.documents_checked_by,
            cd.documents_verified_by,
            cd.loan_approved_by,
            cd.disbursed_by,
            cd.rc_online_checking,
            cd.ho_collection_tool_by,
            c.area_code,
            cd.ho_tvr_done_by,
            cd.stock_marked_to_bank
        FROM contracts c
        LEFT JOIN contract_details cd
          ON cd.contract_id = c.contract_id
        WHERE c.contract_id = :contractId
        ORDER BY cd.contract_detail_id
        LIMIT 1
        """;

    private static final String FIND_CONTRACT_DETAIL_ID_SQL = """
        SELECT contract_detail_id
        FROM contract_details
        WHERE contract_id = :contractId
        ORDER BY contract_detail_id
        LIMIT 1
        """;

    private static final String INSERT_CONTRACT_DETAIL_SQL = """
        INSERT INTO contract_details (
            contract_id,
            document_type,
            document_verified_by,
            documents_obtained_by,
            guarantor_fi_by,
            borrower_fi_by,
            loan_referred_by,
            tvr_done_by,
            vehicle_by_agency,
            vehicle_inspection_by,
            property_valuation_by,
            legal_opinion_by,
            branch_collection_tool_by,
            documents_checked_by,
            documents_verified_by,
            loan_approved_by,
            disbursed_by,
            rc_online_checking,
            ho_collection_tool_by,
            ho_tvr_done_by,
            stock_marked_to_bank,
            created_by,
            is_active
        )
        VALUES (
            :contractId,
            :documentType,
            :documentVerifiedBy,
            :documentsObtainedBy,
            :guarantorFiBy,
            :borrowerFiBy,
            :loanReferredBy,
            :tvrDoneBy,
            :vehicleByAgency,
            :vehicleInspectionBy,
            :propertyValuationBy,
            :legalOpinionBy,
            :branchCollectionToolBy,
            :documentsCheckedBy,
            :documentsVerifiedBy,
            :loanApprovedBy,
            :disbursedBy,
            :rcOnlineChecking,
            :hoCollectionToolBy,
            :hoTvrDoneBy,
            :stockMarkedToBank,
            :updatedBy,
            TRUE
        )
        """;

    private static final String UPDATE_CONTRACT_DETAIL_SQL = """
        UPDATE contract_details
        SET
            document_type = :documentType,
            document_verified_by = :documentVerifiedBy,
            documents_obtained_by = :documentsObtainedBy,
            guarantor_fi_by = :guarantorFiBy,
            borrower_fi_by = :borrowerFiBy,
            loan_referred_by = :loanReferredBy,
            tvr_done_by = :tvrDoneBy,
            vehicle_by_agency = :vehicleByAgency,
            vehicle_inspection_by = :vehicleInspectionBy,
            property_valuation_by = :propertyValuationBy,
            legal_opinion_by = :legalOpinionBy,
            branch_collection_tool_by = :branchCollectionToolBy,
            documents_checked_by = :documentsCheckedBy,
            documents_verified_by = :documentsVerifiedBy,
            loan_approved_by = :loanApprovedBy,
            disbursed_by = :disbursedBy,
            rc_online_checking = :rcOnlineChecking,
            ho_collection_tool_by = :hoCollectionToolBy,
            ho_tvr_done_by = :hoTvrDoneBy,
            stock_marked_to_bank = :stockMarkedToBank,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP,
            is_active = TRUE
        WHERE contract_detail_id = :contractDetailId
        """;

    private static final String UPDATE_CONTRACT_SQL = """
        UPDATE contracts
        SET
            area_code = :areaCode,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP
        WHERE contract_id = :contractId
        """;

    private static final String FIND_PENDING_DOCUMENTS_SQL = """
        SELECT document_name, is_pending
        FROM contract_pending_documents
        WHERE contract_id = :contractId
          AND is_active = TRUE
        ORDER BY pending_document_id
        """;

    private static final String DELETE_PENDING_DOCUMENTS_SQL = """
        DELETE FROM contract_pending_documents
        WHERE contract_id = :contractId
        """;

    private static final String INSERT_PENDING_DOCUMENT_SQL = """
        INSERT INTO contract_pending_documents (
            contract_id,
            document_name,
            is_pending,
            created_by,
            updated_by,
            updated_at,
            is_active
        )
        VALUES (
            :contractId,
            :documentName,
            :pending,
            :updatedBy,
            :updatedBy,
            CURRENT_TIMESTAMP,
            TRUE
        )
        """;

    private static final String FIND_UPLOADS_SQL = """
        SELECT
            document_upload_id,
            document_category,
            file_name,
            file_size,
            content_type,
            storage_path
        FROM contract_document_uploads
        WHERE contract_id = :contractId
          AND is_active = TRUE
        ORDER BY document_upload_id
        """;

    private static final String DELETE_UPLOADS_SQL = """
        DELETE FROM contract_document_uploads
        WHERE contract_id = :contractId
        """;

    private static final String INSERT_UPLOAD_SQL = """
        INSERT INTO contract_document_uploads (
            contract_id,
            document_category,
            file_name,
            file_size,
            content_type,
            storage_path,
            created_by,
            updated_by,
            updated_at,
            is_active
        )
        VALUES (
            :contractId,
            :documentCategory,
            :fileName,
            :fileSize,
            :contentType,
            :storagePath,
            :updatedBy,
            :updatedBy,
            CURRENT_TIMESTAMP,
            TRUE
        )
        """;

    private static final RowMapper<ContractDocumentationDraftDto> DOCUMENTATION_ROW_MAPPER = (rs, rowNum) -> new ContractDocumentationDraftDto(
        rs.getLong("contract_id"),
        rs.getString("document_type"),
        rs.getString("document_verified_by"),
        rs.getString("documents_obtained_by"),
        rs.getString("guarantor_fi_by"),
        rs.getString("borrower_fi_by"),
        rs.getString("loan_referred_by"),
        rs.getString("tvr_done_by"),
        rs.getString("vehicle_by_agency"),
        rs.getString("vehicle_inspection_by"),
        rs.getString("property_valuation_by"),
        rs.getString("legal_opinion_by"),
        rs.getString("branch_collection_tool_by"),
        rs.getString("documents_checked_by"),
        rs.getString("documents_verified_by"),
        rs.getString("loan_approved_by"),
        rs.getString("disbursed_by"),
        rs.getString("rc_online_checking"),
        rs.getString("ho_collection_tool_by"),
        rs.getString("area_code"),
        rs.getString("ho_tvr_done_by"),
        rs.getString("stock_marked_to_bank"),
        List.of(),
        List.of()
    );

    private static final RowMapper<ContractPendingDocumentDto> PENDING_ROW_MAPPER = (rs, rowNum) -> new ContractPendingDocumentDto(
        rs.getString("document_name"),
        rs.getBoolean("is_pending")
    );

    private static final RowMapper<ContractDocumentUploadDto> UPLOAD_ROW_MAPPER = (rs, rowNum) -> new ContractDocumentUploadDto(
        rs.getLong("document_upload_id"),
        rs.getString("document_category"),
        rs.getString("file_name"),
        rs.getObject("file_size", Long.class),
        rs.getString("content_type"),
        rs.getString("storage_path")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ContractDocumentationDraftRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<ContractDocumentationDraftDto> findByContractId(Long contractId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("contractId", contractId);
        List<ContractDocumentationDraftDto> rows = namedParameterJdbcTemplate.query(
            FIND_DOCUMENTATION_SQL,
            params,
            DOCUMENTATION_ROW_MAPPER
        );
        return rows.stream().findFirst()
            .map((documentation) -> withChildren(documentation, findPendingDocuments(contractId), findUploads(contractId)));
    }

    public void updateContract(ContractDocumentationDraftDto documentation, String updatedBy) {
        namedParameterJdbcTemplate.update(
            UPDATE_CONTRACT_SQL,
            params(documentation).addValue("updatedBy", updatedBy)
        );
    }

    public void upsertContractDetail(ContractDocumentationDraftDto documentation, String updatedBy) {
        List<Long> detailIds = namedParameterJdbcTemplate.queryForList(
            FIND_CONTRACT_DETAIL_ID_SQL,
            new MapSqlParameterSource().addValue("contractId", documentation.contractId()),
            Long.class
        );
        MapSqlParameterSource params = params(documentation).addValue("updatedBy", updatedBy);
        if (detailIds.isEmpty()) {
            namedParameterJdbcTemplate.update(INSERT_CONTRACT_DETAIL_SQL, params);
            return;
        }

        namedParameterJdbcTemplate.update(
            UPDATE_CONTRACT_DETAIL_SQL,
            params.addValue("contractDetailId", detailIds.get(0))
        );
    }

    public void replacePendingDocuments(Long contractId, List<ContractPendingDocumentDto> pendingDocuments, String updatedBy) {
        namedParameterJdbcTemplate.update(
            DELETE_PENDING_DOCUMENTS_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId)
        );
        if (pendingDocuments == null) {
            return;
        }
        for (ContractPendingDocumentDto pendingDocument : pendingDocuments) {
            if (pendingDocument == null || isBlank(pendingDocument.documentName())) {
                continue;
            }
            namedParameterJdbcTemplate.update(
                INSERT_PENDING_DOCUMENT_SQL,
                new MapSqlParameterSource()
                    .addValue("contractId", contractId)
                    .addValue("documentName", pendingDocument.documentName().trim())
                    .addValue("pending", Boolean.TRUE.equals(pendingDocument.pending()))
                    .addValue("updatedBy", updatedBy)
            );
        }
    }

    public void replaceUploads(Long contractId, List<ContractDocumentUploadDto> uploads, String updatedBy) {
        namedParameterJdbcTemplate.update(
            DELETE_UPLOADS_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId)
        );
        if (uploads == null) {
            return;
        }
        for (ContractDocumentUploadDto upload : uploads) {
            if (upload == null || isBlank(upload.documentCategory()) || isBlank(upload.fileName())) {
                continue;
            }
            namedParameterJdbcTemplate.update(
                INSERT_UPLOAD_SQL,
                new MapSqlParameterSource()
                    .addValue("contractId", contractId)
                    .addValue("documentCategory", upload.documentCategory().trim())
                    .addValue("fileName", upload.fileName().trim())
                    .addValue("fileSize", upload.fileSize())
                    .addValue("contentType", clean(upload.contentType()))
                    .addValue("storagePath", clean(upload.storagePath()))
                    .addValue("updatedBy", updatedBy)
            );
        }
    }

    public List<ContractPendingDocumentDto> findPendingDocuments(Long contractId) {
        return namedParameterJdbcTemplate.query(
            FIND_PENDING_DOCUMENTS_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            PENDING_ROW_MAPPER
        );
    }

    public List<ContractDocumentUploadDto> findUploads(Long contractId) {
        return namedParameterJdbcTemplate.query(
            FIND_UPLOADS_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            UPLOAD_ROW_MAPPER
        );
    }

    public ContractDocumentationDraftDto withChildren(
        ContractDocumentationDraftDto documentation,
        List<ContractPendingDocumentDto> pendingDocuments,
        List<ContractDocumentUploadDto> uploads
    ) {
        return new ContractDocumentationDraftDto(
            documentation.contractId(),
            documentation.documentType(),
            documentation.documentVerifiedBy(),
            documentation.documentsObtainedBy(),
            documentation.guarantorFiBy(),
            documentation.borrowerFiBy(),
            documentation.loanReferredBy(),
            documentation.tvrDoneBy(),
            documentation.vehicleByAgency(),
            documentation.vehicleInspectionBy(),
            documentation.propertyValuationBy(),
            documentation.legalOpinionBy(),
            documentation.branchCollectionToolBy(),
            documentation.documentsCheckedBy(),
            documentation.documentsVerifiedBy(),
            documentation.loanApprovedBy(),
            documentation.disbursedBy(),
            documentation.rcOnlineChecking(),
            documentation.hoCollectionToolBy(),
            documentation.areaCode(),
            documentation.hoTvrDoneBy(),
            documentation.stockMarkedToBank(),
            pendingDocuments,
            uploads
        );
    }

    private MapSqlParameterSource params(ContractDocumentationDraftDto documentation) {
        return new MapSqlParameterSource()
            .addValue("contractId", documentation.contractId())
            .addValue("documentType", clean(documentation.documentType()))
            .addValue("documentVerifiedBy", clean(documentation.documentVerifiedBy()))
            .addValue("documentsObtainedBy", clean(documentation.documentsObtainedBy()))
            .addValue("guarantorFiBy", clean(documentation.guarantorFiBy()))
            .addValue("borrowerFiBy", clean(documentation.borrowerFiBy()))
            .addValue("loanReferredBy", clean(documentation.loanReferredBy()))
            .addValue("tvrDoneBy", clean(documentation.tvrDoneBy()))
            .addValue("vehicleByAgency", clean(documentation.vehicleByAgency()))
            .addValue("vehicleInspectionBy", clean(documentation.vehicleInspectionBy()))
            .addValue("propertyValuationBy", clean(documentation.propertyValuationBy()))
            .addValue("legalOpinionBy", clean(documentation.legalOpinionBy()))
            .addValue("branchCollectionToolBy", clean(documentation.branchCollectionToolBy()))
            .addValue("documentsCheckedBy", clean(documentation.documentsCheckedBy()))
            .addValue("documentsVerifiedBy", clean(documentation.documentsVerifiedBy()))
            .addValue("loanApprovedBy", clean(documentation.loanApprovedBy()))
            .addValue("disbursedBy", clean(documentation.disbursedBy()))
            .addValue("rcOnlineChecking", clean(documentation.rcOnlineChecking()))
            .addValue("hoCollectionToolBy", clean(documentation.hoCollectionToolBy()))
            .addValue("areaCode", clean(documentation.areaCode()))
            .addValue("hoTvrDoneBy", clean(documentation.hoTvrDoneBy()))
            .addValue("stockMarkedToBank", clean(documentation.stockMarkedToBank()));
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
