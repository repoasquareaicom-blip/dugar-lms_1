package dugar_lms_api.migration.common;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

@Service
public class MigrationRunService {

    private final MigrationRunRepository migrationRunRepository;
    private final TransactionTemplate auditTransactionTemplate;

    public MigrationRunService(
        MigrationRunRepository migrationRunRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.migrationRunRepository = migrationRunRepository;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.auditTransactionTemplate = transactionTemplate;
    }

    public Long startRun(String migrationType, String fileName, String sheetName, int totalRows, String uploadedBy) {
        return auditTransactionTemplate.execute(status ->
            migrationRunRepository.createRun(migrationType, fileName, sheetName, totalRows, uploadedBy)
        );
    }

    public void recordDuplicate(Long migrationRunId, Integer excelRow, String referenceKey, String reason, String sourceData) {
        saveDetail(
            migrationRunId,
            excelRow,
            referenceKey,
            MigrationAuditConstants.DetailResultTypes.DUPLICATE,
            reason,
            sourceData
        );
    }

    public void recordFailure(Long migrationRunId, Integer excelRow, String referenceKey, String reason, String sourceData) {
        saveDetail(
            migrationRunId,
            excelRow,
            referenceKey,
            MigrationAuditConstants.DetailResultTypes.FAILED,
            reason,
            sourceData
        );
    }

    public void recordConflict(Long migrationRunId, Integer excelRow, String referenceKey, String reason, String sourceData) {
        saveDetail(
            migrationRunId,
            excelRow,
            referenceKey,
            MigrationAuditConstants.DetailResultTypes.CONFLICT,
            reason,
            sourceData
        );
    }

    public void recordMissingReference(Long migrationRunId, String referenceKey, String reason, String sourceData) {
        saveDetail(
            migrationRunId,
            null,
            referenceKey,
            MigrationAuditConstants.DetailResultTypes.MISSING_REFERENCE,
            reason,
            sourceData
        );
    }

    public void recordDetail(Long migrationRunId, Integer excelRow, String referenceKey, String resultType, String reason, String sourceData) {
        saveDetail(migrationRunId, excelRow, referenceKey, resultType, reason, sourceData);
    }

    public void completeRun(
        Long migrationRunId,
        int insertedCount,
        int duplicateCount,
        int failedCount,
        String status
    ) {
        auditTransactionTemplate.executeWithoutResult(transactionStatus ->
            migrationRunRepository.completeRun(migrationRunId, insertedCount, duplicateCount, failedCount, status)
        );
    }

    public void failRun(
        Long migrationRunId,
        int insertedCount,
        int duplicateCount,
        int failedCount,
        String reason
    ) {
        auditTransactionTemplate.executeWithoutResult(transactionStatus ->
            migrationRunRepository.markRunFailed(migrationRunId, insertedCount, duplicateCount, failedCount, reason)
        );
    }

    public List<MigrationRun> getRecentRuns(int limit) {
        return migrationRunRepository.findRecentRuns(limit);
    }

    public Optional<MigrationRun> getRunById(Long migrationRunId) {
        return migrationRunRepository.findRunById(migrationRunId);
    }

    public List<MigrationRunDetail> getDetailsByRunId(Long migrationRunId) {
        return migrationRunRepository.findDetailsByRunId(migrationRunId);
    }

    public List<MigrationRunDetail> getDetailsByRunId(Long migrationRunId, String resultType) {
        return migrationRunRepository.findDetailsByRunId(migrationRunId, resultType);
    }

    private void saveDetail(
        Long migrationRunId,
        Integer excelRow,
        String referenceKey,
        String resultType,
        String reason,
        String sourceData
    ) {
        auditTransactionTemplate.executeWithoutResult(transactionStatus ->
            migrationRunRepository.saveDetail(migrationRunId, excelRow, referenceKey, resultType, reason, sourceData)
        );
    }
}
