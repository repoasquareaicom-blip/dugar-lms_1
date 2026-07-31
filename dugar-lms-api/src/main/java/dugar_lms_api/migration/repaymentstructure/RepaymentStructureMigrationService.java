package dugar_lms_api.migration.repaymentstructure;

import dugar_lms_api.migration.common.MigrationAuditConstants;
import dugar_lms_api.migration.common.MigrationRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RepaymentStructureMigrationService {

    static final String SOURCE_SHEET_NAME = "HP_SCHE";
    static final String SOURCE_FILE_NAME = "HP_SCHE_DATA_TABLE.xlsx";
    static final String ORACLE_SOURCE_TABLE = "HP_SCHE";
    static final String TARGET_TABLE = "contract_repayment_structures";
    static final String CREATED_BY = "LEGACY_MIGRATION";

    private static final Logger LOGGER = LoggerFactory.getLogger(RepaymentStructureMigrationService.class);

    private static final String MISSING_CONTRACT = "MISSING_CONTRACT";
    private static final String AMBIGUOUS_CONTRACT = "AMBIGUOUS_CONTRACT";
    private static final String INVALID_SEQUENCE = "INVALID_SEQUENCE";
    private static final String INVALID_INSTALLMENT_COUNT = "INVALID_INSTALLMENT_COUNT";
    private static final String INVALID_INSTALLMENT_AMOUNT = "INVALID_INSTALLMENT_AMOUNT";

    private final RepaymentStructureExcelReader excelReader;
    private final RepaymentStructureMigrationRepository repository;
    private final MigrationRunService migrationRunService;
    private final TransactionTemplate rowTransactionTemplate;
    private final Path migrationDataDirectory;

    @Autowired
    public RepaymentStructureMigrationService(
        RepaymentStructureExcelReader excelReader,
        RepaymentStructureMigrationRepository repository,
        MigrationRunService migrationRunService,
        PlatformTransactionManager transactionManager
    ) {
        this(excelReader, repository, migrationRunService, transactionManager, Path.of("migration_data"));
    }

    RepaymentStructureMigrationService(
        RepaymentStructureExcelReader excelReader,
        RepaymentStructureMigrationRepository repository,
        MigrationRunService migrationRunService,
        PlatformTransactionManager transactionManager,
        Path migrationDataDirectory
    ) {
        this.excelReader = excelReader;
        this.repository = repository;
        this.migrationRunService = migrationRunService;
        this.migrationDataDirectory = migrationDataDirectory;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rowTransactionTemplate = transactionTemplate;
    }

    public RepaymentStructureMigrationSummary prepareRepaymentStructureMigration() {
        Path sourceFile = migrationDataDirectory.resolve(SOURCE_FILE_NAME);
        requireFile(sourceFile, SOURCE_FILE_NAME);

        List<RepaymentStructureSourceRow> rows = readRows(sourceFile);
        RepaymentStructureMigrationSummary summary = RepaymentStructureMigrationSummary.base();
        summary.setTotalSourceRows(nonBlankCount(rows));
        validateSchemaForSummary(summary);
        if (!summary.isSuccess()) {
            return summary;
        }

        SourceKeyTracker tracker = new SourceKeyTracker();
        for (RepaymentStructureSourceRow row : rows) {
            handleRow(null, row, summary, tracker, false);
        }
        logSummary("Before completeProcessStatus", summary);
        summary.completeProcessStatus();
        logSummary("After completeProcessStatus", summary);
        logSummary("Before returning from RepaymentStructureMigrationService#prepare", summary);
        return summary;
    }

    public RepaymentStructureMigrationSummary importRepaymentStructures() {
        Path sourceFile = migrationDataDirectory.resolve(SOURCE_FILE_NAME);
        requireFile(sourceFile, SOURCE_FILE_NAME);

        List<RepaymentStructureSourceRow> rows = readRows(sourceFile);
        RepaymentStructureMigrationSummary summary = RepaymentStructureMigrationSummary.base();
        summary.setTotalSourceRows(nonBlankCount(rows));

        Long migrationRunId = migrationRunService.startRun(
            MigrationAuditConstants.MigrationTypes.REPAYMENT_STRUCTURE,
            SOURCE_FILE_NAME,
            SOURCE_SHEET_NAME,
            summary.getTotalSourceRows(),
            CREATED_BY
        );
        summary.setMigrationRunId(migrationRunId);

        List<String> schemaIssues = validateRuntimeSchema();
        if (!schemaIssues.isEmpty()) {
            summary.setSuccess(false);
            summary.setFailed(summary.getTotalSourceRows());
            for (String issue : schemaIssues) {
                summary.addIssue(null, null, MigrationAuditConstants.DetailResultTypes.FAILED, issue);
                migrationRunService.recordFailure(migrationRunId, null, null, issue, null);
            }
            migrationRunService.failRun(migrationRunId, 0, 0, summary.getFailed(), "Schema validation failed");
            return summary;
        }

        SourceKeyTracker tracker = new SourceKeyTracker();
        for (RepaymentStructureSourceRow row : rows) {
            handleRow(migrationRunId, row, summary, tracker, true);
        }

        logSummary("Before completeProcessStatus", summary);
        summary.completeProcessStatus();
        logSummary("After completeProcessStatus", summary);
        migrationRunService.completeRun(
            migrationRunId,
            summary.getInserted(),
            summary.getDuplicates(),
            summary.getFailed(),
            finalRunStatus(summary)
        );
        logSummary("Before returning from RepaymentStructureMigrationService#importRepaymentStructures", summary);
        return summary;
    }

    RowOutcome importRow(RepaymentStructureSourceRow row, SourceKeyTracker tracker, boolean insert) {
        if (row.isBlank()) {
            return RowOutcome.SKIPPED_BLANK;
        }
        if (row.isRepeatedHeader()) {
            return RowOutcome.SKIPPED_HEADER;
        }

        String contractType = requiredContract(row.contractType(), "CONT_TYPE");
        String contractNumber = requiredContract(row.contractNumber(), "CONT_NO");
        Integer sequenceNo = parsePositiveWhole(row.sequenceNo(), "SNO", INVALID_SEQUENCE);
        Integer installmentCount = parsePositiveWhole(row.numberOfInstallments(), "NO_OF_INST", INVALID_INSTALLMENT_COUNT);
        BigDecimal installmentAmount = parsePositiveAmount(row.installmentAmount());

        List<Long> contractIds = repository.findContractIds(contractType, contractNumber);
        if (contractIds.isEmpty()) {
            throw new MissingContractException("Missing contract for " + referenceKey(contractType, contractNumber));
        }
        if (contractIds.size() > 1) {
            throw new AmbiguousContractException("Multiple contracts for " + referenceKey(contractType, contractNumber));
        }

        RepaymentStructureRecord record = new RepaymentStructureRecord(contractIds.getFirst(), sequenceNo, installmentCount, installmentAmount);
        SourceKeyOutcome sourceOutcome = tracker.track(record);
        if (sourceOutcome == SourceKeyOutcome.DUPLICATE) {
            return RowOutcome.DUPLICATE;
        }
        if (sourceOutcome == SourceKeyOutcome.CONFLICT) {
            throw new RepaymentStructureConflictException("Conflicting source rows for contract_id=" + record.contractId() + ", sequence_no=" + record.sequenceNo());
        }

        Optional<ExistingRepaymentStructure> existing = repository.findExisting(record.contractId(), record.sequenceNo());
        if (existing.isPresent()) {
            if (sameValues(existing.get(), record)) {
                return RowOutcome.DUPLICATE;
            }
            throw new RepaymentStructureConflictException("Existing repayment structure differs: existing count="
                + existing.get().numberOfInstallments() + ", amount=" + existing.get().installmentAmount()
                + "; incoming count=" + record.numberOfInstallments() + ", amount=" + record.installmentAmount());
        }

        if (insert) {
            repository.insert(record);
        }
        return RowOutcome.INSERTED;
    }

    private void handleRow(
        Long migrationRunId,
        RepaymentStructureSourceRow row,
        RepaymentStructureMigrationSummary summary,
        SourceKeyTracker tracker,
        boolean insert
    ) {
        try {
            RowOutcome outcome = rowTransactionTemplate.execute(status -> importRow(row, tracker, insert));
            handleOutcome(migrationRunId, row, summary, outcome, insert);
        } catch (MissingContractException exception) {
            summary.incrementMissingContracts();
            addIssueAndAudit(migrationRunId, row, summary, MISSING_CONTRACT, exception.getMessage(), sourceData(row));
        } catch (AmbiguousContractException exception) {
            summary.incrementConflicts();
            addIssueAndAudit(migrationRunId, row, summary, AMBIGUOUS_CONTRACT, exception.getMessage(), sourceData(row));
        } catch (InvalidSequenceException exception) {
            summary.incrementInvalidSequences();
            addIssueAndAudit(migrationRunId, row, summary, INVALID_SEQUENCE, exception.getMessage(), sourceData(row));
        } catch (InvalidInstallmentCountException exception) {
            summary.incrementInvalidInstallmentCounts();
            addIssueAndAudit(migrationRunId, row, summary, INVALID_INSTALLMENT_COUNT, exception.getMessage(), sourceData(row));
        } catch (InvalidInstallmentAmountException exception) {
            summary.incrementInvalidInstallmentAmounts();
            addIssueAndAudit(migrationRunId, row, summary, INVALID_INSTALLMENT_AMOUNT, exception.getMessage(), sourceData(row));
        } catch (RepaymentStructureConflictException exception) {
            summary.incrementConflicts();
            addIssueAndAudit(migrationRunId, row, summary, MigrationAuditConstants.DetailResultTypes.CONFLICT, exception.getMessage(), sourceData(row));
        } catch (Exception exception) {
            LOGGER.error("Repayment structure row failed", exception);
            String reason = "Row processing failed";
            summary.incrementFailed();
            addIssueAndAudit(migrationRunId, row, summary, MigrationAuditConstants.DetailResultTypes.FAILED, reason, sourceData(row));
        }
    }

    private void handleOutcome(
        Long migrationRunId,
        RepaymentStructureSourceRow row,
        RepaymentStructureMigrationSummary summary,
        RowOutcome outcome,
        boolean insert
    ) {
        if (outcome == RowOutcome.SKIPPED_BLANK) {
            summary.incrementSkippedBlankRows();
            return;
        }
        if (outcome == RowOutcome.SKIPPED_HEADER) {
            summary.incrementRepeatedHeaderRows();
            return;
        }
        summary.incrementEligibleRows();
        if (outcome == RowOutcome.INSERTED) {
            if (insert) {
                summary.incrementInserted();
                audit(migrationRunId, row, MigrationAuditConstants.DetailResultTypes.INSERTED, "Inserted repayment structure", sourceData(row));
            }
        } else if (outcome == RowOutcome.DUPLICATE) {
            summary.incrementDuplicates();
            addIssueAndAudit(migrationRunId, row, summary, MigrationAuditConstants.DetailResultTypes.DUPLICATE, "Identical repayment structure already exists", sourceData(row));
        }
    }

    private void addIssueAndAudit(
        Long migrationRunId,
        RepaymentStructureSourceRow row,
        RepaymentStructureMigrationSummary summary,
        String type,
        String reason,
        String sourceData
    ) {
        summary.addIssue(row.excelRow(), safeReferenceKey(row), type, reason);
        audit(migrationRunId, row, type, reason, sourceData);
    }

    private void audit(Long migrationRunId, RepaymentStructureSourceRow row, String type, String reason, String sourceData) {
        if (migrationRunId == null) {
            return;
        }
        migrationRunService.recordDetail(migrationRunId, row.excelRow(), safeReferenceKey(row), type, reason, sourceData);
    }

    private void validateSchemaForSummary(RepaymentStructureMigrationSummary summary) {
        List<String> schemaIssues = validateRuntimeSchema();
        if (!schemaIssues.isEmpty()) {
            summary.setSuccess(false);
            for (String issue : schemaIssues) {
                summary.addIssue(null, null, MigrationAuditConstants.DetailResultTypes.FAILED, issue);
            }
        }
    }

    private List<String> validateRuntimeSchema() {
        Set<String> columns = repository.tableColumns(TARGET_TABLE);
        Set<String> required = Set.of(
            "repayment_structure_id",
            "contract_id",
            "sequence_no",
            "number_of_installments",
            "installment_amount",
            "created_at",
            "updated_at"
        );
        return required.stream()
            .filter(column -> !columns.contains(column))
            .map(column -> "Schema mismatch: missing column " + TARGET_TABLE + "." + column)
            .toList();
    }

    private List<RepaymentStructureSourceRow> readRows(Path sourceFile) {
        try (InputStream inputStream = Files.newInputStream(sourceFile)) {
            return excelReader.read(inputStream, SOURCE_SHEET_NAME);
        } catch (IOException exception) {
            LOGGER.error("Unable to read repayment structure workbook", exception);
            throw new IllegalArgumentException("Unable to read source file: " + SOURCE_FILE_NAME, exception);
        }
    }

    private void requireFile(Path path, String fileName) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Missing source file: " + fileName);
        }
    }

    private int nonBlankCount(List<RepaymentStructureSourceRow> rows) {
        return (int) rows.stream().filter(row -> !row.isBlank() && !row.isRepeatedHeader()).count();
    }

    private String requiredContract(String value, String header) {
        String normalized = normalizeContract(value);
        if (normalized == null) {
            throw new IllegalArgumentException(header + " is required");
        }
        return normalized;
    }

    String normalizeContract(String value) {
        String normalized = nullIfBlank(value);
        if (normalized == null) {
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(normalized.replace(",", ""));
            return decimal.stripTrailingZeros().toPlainString().toUpperCase(Locale.ROOT);
        } catch (NumberFormatException ignored) {
            return normalized.toUpperCase(Locale.ROOT);
        }
    }

    private Integer parsePositiveWhole(String value, String header, String type) {
        String normalized = nullIfBlank(value);
        if (normalized == null) {
            throw invalidWhole(type, header + " is required");
        }
        try {
            BigDecimal decimal = new BigDecimal(normalized.replace(",", ""));
            if (decimal.compareTo(BigDecimal.ZERO) <= 0 || decimal.stripTrailingZeros().scale() > 0) {
                throw invalidWhole(type, "Invalid positive whole number for " + header + ": " + normalized);
            }
            return decimal.intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw invalidWhole(type, "Invalid positive whole number for " + header + ": " + normalized);
        }
    }

    private RuntimeException invalidWhole(String type, String reason) {
        return INVALID_SEQUENCE.equals(type)
            ? new InvalidSequenceException(reason)
            : new InvalidInstallmentCountException(reason);
    }

    private BigDecimal parsePositiveAmount(String value) {
        String normalized = nullIfBlank(value);
        if (normalized == null) {
            throw new InvalidInstallmentAmountException("INST_AMT is required");
        }
        try {
            BigDecimal amount = new BigDecimal(normalized.replace(",", ""));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidInstallmentAmountException("Invalid positive amount for INST_AMT: " + normalized);
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new InvalidInstallmentAmountException("Invalid positive amount for INST_AMT: " + normalized);
        }
    }

    private boolean sameValues(ExistingRepaymentStructure existing, RepaymentStructureRecord incoming) {
        return existing.numberOfInstallments().equals(incoming.numberOfInstallments())
            && existing.installmentAmount().compareTo(incoming.installmentAmount()) == 0;
    }

    private String nullIfBlank(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String finalRunStatus(RepaymentStructureMigrationSummary summary) {
        return summary.isSuccess()
            ? MigrationAuditConstants.RunStatuses.COMPLETED
            : MigrationAuditConstants.RunStatuses.FAILED;
    }

    private String safeReferenceKey(RepaymentStructureSourceRow row) {
        return referenceKey(normalizeContract(row.contractType()), normalizeContract(row.contractNumber()));
    }

    private String referenceKey(String contractType, String contractNumber) {
        return safe(contractType) + "/" + safe(contractNumber);
    }

    private String sourceData(RepaymentStructureSourceRow row) {
        return "{\"contractType\":\"" + safe(row.contractType()) + "\",\"contractNumber\":\"" + safe(row.contractNumber())
            + "\",\"sequenceNo\":\"" + safe(row.sequenceNo()) + "\"}";
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Repayment structure row failed" : current.getMessage();
    }

    private void logSummary(String message, RepaymentStructureMigrationSummary summary) {
        LOGGER.info(
            "{} success={} failed={} inserted={} duplicates={} conflicts={} missingContracts={} completedWithWarnings={}",
            message,
            summary.isSuccess(),
            summary.getFailed(),
            summary.getInserted(),
            summary.getDuplicates(),
            summary.getConflicts(),
            summary.getMissingContracts(),
            summary.isCompletedWithWarnings()
        );
    }

    enum RowOutcome {
        INSERTED,
        DUPLICATE,
        SKIPPED_BLANK,
        SKIPPED_HEADER
    }

    enum SourceKeyOutcome {
        NEW,
        DUPLICATE,
        CONFLICT
    }

    static class SourceKeyTracker {
        private final Map<String, RepaymentStructureRecord> records = new LinkedHashMap<>();

        SourceKeyOutcome track(RepaymentStructureRecord record) {
            String key = record.contractId() + "/" + record.sequenceNo();
            RepaymentStructureRecord existing = records.putIfAbsent(key, record);
            if (existing == null) {
                return SourceKeyOutcome.NEW;
            }
            if (existing.numberOfInstallments().equals(record.numberOfInstallments())
                && existing.installmentAmount().compareTo(record.installmentAmount()) == 0) {
                return SourceKeyOutcome.DUPLICATE;
            }
            return SourceKeyOutcome.CONFLICT;
        }
    }

    static class MissingContractException extends RuntimeException {
        MissingContractException(String message) { super(message); }
    }

    static class AmbiguousContractException extends RuntimeException {
        AmbiguousContractException(String message) { super(message); }
    }

    static class InvalidSequenceException extends RuntimeException {
        InvalidSequenceException(String message) { super(message); }
    }

    static class InvalidInstallmentCountException extends RuntimeException {
        InvalidInstallmentCountException(String message) { super(message); }
    }

    static class InvalidInstallmentAmountException extends RuntimeException {
        InvalidInstallmentAmountException(String message) { super(message); }
    }

    static class RepaymentStructureConflictException extends RuntimeException {
        RepaymentStructureConflictException(String message) { super(message); }
    }
}
