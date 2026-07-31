package dugar_lms_api.migration.assetinsurance;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AssetInsuranceMigrationService {

    static final String MAPPING_FILE_NAME = "Master data LMS(1).xlsx";
    static final String MAPPING_SHEET_NAME = "Assets Details";
    static final String SOURCE_FILE_NAME = "HP_INSURANCE_DATA_TABLE.xlsx";
    static final String SOURCE_SHEET_NAME = "HP_INSURANCE";
    static final String ORACLE_SOURCE_TABLE = "HP_INSURANCE";
    static final String TARGET_TABLE = "asset_insurances";
    static final String CREATED_BY = "LEGACY_MIGRATION";

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetInsuranceMigrationService.class);

    private static final String DUPLICATE_SOURCE_ROW = "DUPLICATE_SOURCE_ROW";
    private static final String MISSING_CONTRACT = "MISSING_CONTRACT";
    private static final String MISSING_ASSET = "MISSING_ASSET";
    private static final String AMBIGUOUS_ASSET = "AMBIGUOUS_ASSET";
    private static final String INVALID_DATE = "INVALID_DATE";
    private static final String INVALID_AMOUNT = "INVALID_AMOUNT";

    private final AssetInsuranceExcelReader excelReader;
    private final AssetInsuranceMigrationRepository repository;
    private final MigrationRunService migrationRunService;
    private final TransactionTemplate rowTransactionTemplate;
    private final Path migrationDataDirectory;

    @Autowired
    public AssetInsuranceMigrationService(
        AssetInsuranceExcelReader excelReader,
        AssetInsuranceMigrationRepository repository,
        MigrationRunService migrationRunService,
        PlatformTransactionManager transactionManager
    ) {
        this(excelReader, repository, migrationRunService, transactionManager, Path.of("migration_data"));
    }

    AssetInsuranceMigrationService(
        AssetInsuranceExcelReader excelReader,
        AssetInsuranceMigrationRepository repository,
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

    public AssetInsuranceMigrationSummary importAssetInsurances() {
        Path sourceFile = migrationDataDirectory.resolve(SOURCE_FILE_NAME);
        requireFile(sourceFile, SOURCE_FILE_NAME);

        List<AssetInsuranceSourceRow> rows = readRows(sourceFile);
        AssetInsuranceMigrationSummary summary = AssetInsuranceMigrationSummary.base();
        summary.setTotalSourceRows(rows.size());
        Long migrationRunId = migrationRunService.startRun(
            MigrationAuditConstants.MigrationTypes.ASSET_INSURANCE,
            SOURCE_FILE_NAME,
            SOURCE_SHEET_NAME,
            rows.size(),
            CREATED_BY
        );
        summary.setMigrationRunId(migrationRunId);

        List<String> schemaIssues = validateRuntimeSchema();
        if (!schemaIssues.isEmpty()) {
            summary.setSuccess(false);
            summary.setFailed(rows.size());
            for (String issue : schemaIssues) {
                summary.addIssue(null, null, MigrationAuditConstants.DetailResultTypes.FAILED, issue);
                migrationRunService.recordFailure(migrationRunId, null, null, issue, null);
            }
            migrationRunService.failRun(migrationRunId, 0, 0, summary.getFailed(), "Schema validation failed");
            return summary;
        }

        SourceRowTracker sourceRowTracker = new SourceRowTracker();
        for (AssetInsuranceSourceRow row : rows) {
            handleRow(migrationRunId, row, summary, sourceRowTracker);
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
        logSummary("Before returning from AssetInsuranceMigrationService#importAssetInsurances", summary);
        return summary;
    }

    public AssetInsuranceMigrationSummary prepareAssetInsuranceMigration() {
        AssetInsuranceMigrationSummary summary = AssetInsuranceMigrationSummary.base();
        List<String> schemaIssues = validateRuntimeSchema();
        if (!schemaIssues.isEmpty()) {
            summary.setSuccess(false);
            schemaIssues.forEach(issue -> summary.addIssue(null, null, MigrationAuditConstants.DetailResultTypes.FAILED, issue));
        }
        return summary;
    }

    RowOutcome importRow(AssetInsuranceSourceRow row, SourceRowTracker sourceRowTracker) {
        if (row.isBlank()) {
            return RowOutcome.SKIPPED_BLANK;
        }
        if (row.isRepeatedHeader()) {
            return RowOutcome.SKIPPED_HEADER;
        }

        String contractType = normalizeContract(row.contractType());
        String contractNumber = normalizeContract(row.contractNumber());
        if (contractType == null || contractNumber == null) {
            throw new IllegalArgumentException("CONT_TYPE and CONT_NO are required");
        }

        Optional<Long> contractId = repository.findContractId(contractType, contractNumber);
        if (contractId.isEmpty()) {
            throw new MissingContractException("Missing contract for " + referenceKey(contractType, contractNumber));
        }

        AssetCandidate asset = resolveAsset(contractId.get(), contractType, contractNumber);
        AssetInsuranceRecord incoming = toRecord(row, asset.assetId());
        SourceRowOutcome sourceRowOutcome = sourceRowTracker.track(incoming);
        if (sourceRowOutcome == SourceRowOutcome.DUPLICATE_SOURCE_ROW) {
            return RowOutcome.DUPLICATE_SOURCE_ROW;
        }
        if (sourceRowOutcome == SourceRowOutcome.SOURCE_CONFLICT) {
            throw new AssetInsuranceConflictException("Multiple different source rows for same asset and policy number");
        }

        List<AssetInsuranceExistingRecord> existingRows = existingRows(incoming);
        if (!existingRows.isEmpty()) {
            AssetInsuranceExistingRecord existing = existingRows.getFirst();
            List<String> differences = differences(existing, incoming);
            if (differences.isEmpty()) {
                return RowOutcome.DUPLICATE;
            }
            throw new AssetInsuranceConflictException("Existing insurance has different mapped values: " + String.join(", ", differences));
        }

        repository.insert(incoming);
        return RowOutcome.INSERTED;
    }

    AssetInsuranceRecord toRecord(AssetInsuranceSourceRow row, Long assetId) {
        String insuranceCompanyCode = nullIfPlaceholder(row.insuranceCompanyCode());
        String policyNumber = nullIfPlaceholder(row.policyNumber());
        String coverNoteNumber = nullIfPlaceholder(row.coverNoteNumber());
        LocalDate policyDate = parseDate(row.policyDate(), "POLICY_DT");
        LocalDate validFrom = parseDate(row.validFrom(), "VALID_FROM");
        LocalDate validTo = parseDate(row.validTo(), "VALID_TO");
        BigDecimal premiumAmount = parseAmount(row.premiumAmount());

        return new AssetInsuranceRecord(
            assetId,
            insuranceCompanyCode,
            policyNumber,
            normalizeIdentifier(policyNumber),
            coverNoteNumber,
            policyDate,
            validFrom,
            validTo,
            nullIfPlaceholder(row.policyBy()),
            premiumAmount,
            sourceRowHash(
                assetId,
                insuranceCompanyCode,
                policyNumber,
                coverNoteNumber,
                policyDate,
                validFrom,
                validTo,
                row.policyBy(),
                premiumAmount
            )
        );
    }

    private void handleRow(
        Long migrationRunId,
        AssetInsuranceSourceRow row,
        AssetInsuranceMigrationSummary summary,
        SourceRowTracker sourceRowTracker
    ) {
        try {
            if (row.isBlank()) {
                summary.incrementSkippedBlankRows();
                return;
            }
            if (row.isRepeatedHeader()) {
                summary.incrementRepeatedHeaderRows();
                return;
            }

            summary.incrementEligibleRows();
            countBlankFields(row, summary);

            RowOutcome outcome = rowTransactionTemplate.execute(status -> importRow(row, sourceRowTracker));
            handleOutcome(migrationRunId, row, outcome, summary);
        } catch (MissingContractException exception) {
            String referenceKey = safeReferenceKey(row);
            summary.incrementMissingContracts();
            summary.addIssue(row.excelRow(), referenceKey, MISSING_CONTRACT, exception.getMessage());
            migrationRunService.recordMissingReference(migrationRunId, referenceKey, exception.getMessage(), sourceData(row));
        } catch (MissingAssetException exception) {
            String referenceKey = safeReferenceKey(row);
            summary.incrementMissingAssets();
            summary.addIssue(row.excelRow(), referenceKey, MISSING_ASSET, exception.getMessage());
            migrationRunService.recordMissingReference(migrationRunId, referenceKey, exception.getMessage(), sourceData(row));
        } catch (AmbiguousAssetException exception) {
            String referenceKey = safeReferenceKey(row);
            summary.incrementAmbiguousAssets();
            summary.addIssue(row.excelRow(), referenceKey, AMBIGUOUS_ASSET, exception.getMessage());
            migrationRunService.recordConflict(migrationRunId, row.excelRow(), referenceKey, exception.getMessage(), sourceData(row));
        } catch (InvalidDateException exception) {
            String referenceKey = safeReferenceKey(row);
            summary.incrementInvalidDates();
            summary.incrementFailed();
            summary.addIssue(row.excelRow(), referenceKey, INVALID_DATE, exception.getMessage());
            migrationRunService.recordFailure(migrationRunId, row.excelRow(), referenceKey, exception.getMessage(), sourceData(row));
        } catch (InvalidAmountException exception) {
            String referenceKey = safeReferenceKey(row);
            summary.incrementInvalidAmounts();
            summary.incrementFailed();
            summary.addIssue(row.excelRow(), referenceKey, INVALID_AMOUNT, exception.getMessage());
            migrationRunService.recordFailure(migrationRunId, row.excelRow(), referenceKey, exception.getMessage(), sourceData(row));
        } catch (AssetInsuranceConflictException exception) {
            String referenceKey = safeReferenceKey(row);
            summary.incrementConflicts();
            summary.addIssue(row.excelRow(), referenceKey, MigrationAuditConstants.DetailResultTypes.CONFLICT, exception.getMessage());
            migrationRunService.recordConflict(migrationRunId, row.excelRow(), referenceKey, exception.getMessage(), sourceData(row));
        } catch (Exception exception) {
            String referenceKey = safeReferenceKey(row);
            String reason = rootMessage(exception);
            summary.incrementFailed();
            summary.addIssue(row.excelRow(), referenceKey, MigrationAuditConstants.DetailResultTypes.FAILED, reason);
            migrationRunService.recordFailure(migrationRunId, row.excelRow(), referenceKey, reason, sourceData(row));
        }
    }

    private void handleOutcome(
        Long migrationRunId,
        AssetInsuranceSourceRow row,
        RowOutcome outcome,
        AssetInsuranceMigrationSummary summary
    ) {
        String referenceKey = safeReferenceKey(row);
        if (outcome == RowOutcome.INSERTED) {
            summary.incrementInserted();
        } else if (outcome == RowOutcome.DUPLICATE) {
            summary.incrementDuplicates();
            summary.addIssue(row.excelRow(), referenceKey, MigrationAuditConstants.DetailResultTypes.DUPLICATE, "Identical asset insurance already exists");
            migrationRunService.recordDuplicate(migrationRunId, row.excelRow(), referenceKey, "Identical asset insurance already exists", sourceData(row));
        } else if (outcome == RowOutcome.DUPLICATE_SOURCE_ROW) {
            summary.incrementDuplicates();
            summary.addIssue(row.excelRow(), referenceKey, DUPLICATE_SOURCE_ROW, "Repeated identical source row");
            migrationRunService.recordDuplicate(migrationRunId, row.excelRow(), referenceKey, "Repeated identical source row", sourceData(row));
        }
    }

    private AssetCandidate resolveAsset(Long contractId, String contractType, String contractNumber) {
        List<AssetCandidate> assets = repository.findAssetsByContractId(contractId);
        if (assets.isEmpty()) {
            throw new MissingAssetException("Missing asset for " + referenceKey(contractType, contractNumber));
        }
        if (assets.size() == 1) {
            return assets.getFirst();
        }

        Map<String, AssetCandidate> distinctAssets = new LinkedHashMap<>();
        for (AssetCandidate asset : assets) {
            distinctAssets.putIfAbsent(assetSignature(asset), asset);
        }
        if (distinctAssets.size() == 1) {
            return distinctAssets.values().iterator().next();
        }
        throw new AmbiguousAssetException("Multiple distinct assets for " + referenceKey(contractType, contractNumber));
    }

    private List<AssetInsuranceExistingRecord> existingRows(AssetInsuranceRecord record) {
        if (record.normalizedPolicyNumber() != null) {
            return repository.findByPolicyNumber(record.assetId(), record.normalizedPolicyNumber());
        }
        return repository.findByFallbackKey(record);
    }

    private List<String> differences(AssetInsuranceExistingRecord existing, AssetInsuranceRecord incoming) {
        List<String> differences = new ArrayList<>();
        compare(differences, "insurance_company_code", normalizeIdentifier(existing.insuranceCompanyCode()), normalizeIdentifier(incoming.insuranceCompanyCode()));
        compare(differences, "policy_number", normalizeIdentifier(existing.policyNumber()), incoming.normalizedPolicyNumber());
        compare(differences, "cover_note_number", normalizeIdentifier(existing.coverNoteNumber()), normalizeIdentifier(incoming.coverNoteNumber()));
        compare(differences, "policy_date", existing.policyDate(), incoming.policyDate());
        compare(differences, "valid_from", existing.validFrom(), incoming.validFrom());
        compare(differences, "valid_to", existing.validTo(), incoming.validTo());
        compare(differences, "policy_by", normalizeIdentifier(existing.policyBy()), normalizeIdentifier(incoming.policyBy()));
        compare(differences, "premium_amount", existing.premiumAmount(), incoming.premiumAmount());
        return differences;
    }

    private void compare(List<String> differences, String field, Object existing, Object incoming) {
        if (existing instanceof BigDecimal existingDecimal && incoming instanceof BigDecimal incomingDecimal) {
            if (existingDecimal.compareTo(incomingDecimal) != 0) {
                differences.add(field);
            }
            return;
        }
        if (existing == null && incoming == null) {
            return;
        }
        if (existing == null || incoming == null || !existing.equals(incoming)) {
            differences.add(field);
        }
    }

    private void countBlankFields(AssetInsuranceSourceRow row, AssetInsuranceMigrationSummary summary) {
        if (normalizeIdentifier(row.policyNumber()) == null) {
            summary.incrementBlankPolicyNumbers();
        }
        if (normalizeIdentifier(row.insuranceCompanyCode()) == null) {
            summary.incrementBlankInsuranceCompanyCodes();
        }
        if (parseNullableDate(row.validTo()) == null) {
            summary.incrementBlankValidToDates();
        }
    }

    private List<String> validateRuntimeSchema() {
        Set<String> columns = repository.tableColumns(TARGET_TABLE);
        Set<String> required = Set.of(
            "asset_insurance_id",
            "asset_id",
            "insurance_company_code",
            "policy_number",
            "cover_note_number",
            "policy_date",
            "valid_from",
            "valid_to",
            "policy_by",
            "premium_amount",
            "source_row_hash",
            "created_at",
            "updated_at"
        );
        return required.stream()
            .filter(column -> !columns.contains(column))
            .map(column -> "Schema mismatch: missing column " + TARGET_TABLE + "." + column)
            .toList();
    }

    private List<AssetInsuranceSourceRow> readRows(Path sourceFile) {
        try (InputStream inputStream = Files.newInputStream(sourceFile)) {
            return excelReader.read(inputStream, SOURCE_SHEET_NAME);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read source file: " + SOURCE_FILE_NAME, exception);
        }
    }

    private LocalDate parseDate(String value, String headerName) {
        String normalized = nullIfPlaceholder(value);
        if (normalized == null) {
            return null;
        }
        LocalDate parsed = parseNullableDate(normalized);
        if (parsed == null) {
            throw new InvalidDateException("Invalid date for " + headerName + ": " + normalized);
        }
        return parsed;
    }

    private LocalDate parseNullableDate(String value) {
        String normalized = nullIfPlaceholder(value);
        if (normalized == null) {
            return null;
        }
        String dateText = normalized;
        int timeIndex = normalized.indexOf(' ');
        if (timeIndex > 0) {
            dateText = normalized.substring(0, timeIndex);
        }

        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ofPattern("M/d/uu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-uu", Locale.ENGLISH)
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateText, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private BigDecimal parseAmount(String value) {
        String normalized = nullIfPlaceholder(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new InvalidAmountException("Invalid amount for POLICY_AMT: " + normalized);
        }
    }

    private String sourceRowHash(
        Long assetId,
        String insuranceCompanyCode,
        String policyNumber,
        String coverNoteNumber,
        LocalDate policyDate,
        LocalDate validFrom,
        LocalDate validTo,
        String policyBy,
        BigDecimal premiumAmount
    ) {
        String payload = String.join("|",
            safe(String.valueOf(assetId)),
            safe(normalizeIdentifier(insuranceCompanyCode)),
            safe(normalizeIdentifier(policyNumber)),
            safe(normalizeIdentifier(coverNoteNumber)),
            safe(policyDate == null ? null : policyDate.toString()),
            safe(validFrom == null ? null : validFrom.toString()),
            safe(validTo == null ? null : validTo.toString()),
            safe(normalizeIdentifier(policyBy)),
            safe(premiumAmount == null ? null : premiumAmount.stripTrailingZeros().toPlainString())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String assetSignature(AssetCandidate asset) {
        return String.join("|",
            safe(normalizeRegistration(asset.registrationNumber())),
            safe(normalizeIdentifier(asset.engineNumber())),
            safe(normalizeIdentifier(asset.chassisNumber())),
            safe(asset.sourceRowHash())
        );
    }

    String normalizeContract(String value) {
        return normalizeIdentifier(value);
    }

    String normalizeIdentifier(String value) {
        String normalized = nullIfPlaceholder(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    String normalizeRegistration(String value) {
        String normalized = normalizeIdentifier(value);
        return normalized == null ? null : normalized.replace(" ", "").replace("-", "");
    }

    private String nullIfPlaceholder(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (Set.of(".", "..", "NULL", "N/A", "NIL", "NONE", "-").contains(normalized.toUpperCase(Locale.ROOT))) {
            return null;
        }
        return normalized;
    }

    private void requireFile(Path path, String fileName) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Source file not found: " + fileName);
        }
    }

    private String finalRunStatus(AssetInsuranceMigrationSummary summary) {
        return summary.getFailed() == 0
            ? MigrationAuditConstants.RunStatuses.COMPLETED
            : MigrationAuditConstants.RunStatuses.FAILED;
    }

    private String safeReferenceKey(AssetInsuranceSourceRow row) {
        return referenceKey(normalizeContract(row.contractType()), normalizeContract(row.contractNumber()));
    }

    private String referenceKey(String contractType, String contractNumber) {
        return safe(contractType) + "/" + safe(contractNumber);
    }

    private String sourceData(AssetInsuranceSourceRow row) {
        return "{\"contractType\":\"" + safe(row.contractType()) + "\",\"contractNumber\":\"" + safe(row.contractNumber())
            + "\",\"policyNumber\":\"" + safe(row.policyNumber()) + "\",\"insuranceCompanyCode\":\"" + safe(row.insuranceCompanyCode()) + "\"}";
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Asset insurance import failed" : current.getMessage();
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void logSummary(String message, AssetInsuranceMigrationSummary summary) {
        LOGGER.info(
            "{} success={} failed={} inserted={} duplicates={} conflicts={} missingContracts={} missingAssets={} ambiguousAssets={} completedWithWarnings={}",
            message,
            summary.isSuccess(),
            summary.getFailed(),
            summary.getInserted(),
            summary.getDuplicates(),
            summary.getConflicts(),
            summary.getMissingContracts(),
            summary.getMissingAssets(),
            summary.getAmbiguousAssets(),
            summary.isCompletedWithWarnings()
        );
    }

    enum RowOutcome {
        INSERTED,
        DUPLICATE,
        DUPLICATE_SOURCE_ROW,
        SKIPPED_BLANK,
        SKIPPED_HEADER
    }

    enum SourceRowOutcome {
        NEW,
        DUPLICATE_SOURCE_ROW,
        SOURCE_CONFLICT
    }

    static class SourceRowTracker {
        private final Set<String> exactRows = new LinkedHashSet<>();
        private final Map<String, String> policyRows = new LinkedHashMap<>();

        SourceRowOutcome track(AssetInsuranceRecord record) {
            if (!exactRows.add(record.sourceRowHash())) {
                return SourceRowOutcome.DUPLICATE_SOURCE_ROW;
            }
            if (record.normalizedPolicyNumber() == null) {
                return SourceRowOutcome.NEW;
            }

            String key = record.assetId() + "/" + record.normalizedPolicyNumber();
            String existingHash = policyRows.putIfAbsent(key, record.sourceRowHash());
            if (existingHash != null && !existingHash.equals(record.sourceRowHash())) {
                return SourceRowOutcome.SOURCE_CONFLICT;
            }
            return SourceRowOutcome.NEW;
        }
    }

    static class MissingContractException extends RuntimeException {
        MissingContractException(String message) { super(message); }
    }

    static class MissingAssetException extends RuntimeException {
        MissingAssetException(String message) { super(message); }
    }

    static class AmbiguousAssetException extends RuntimeException {
        AmbiguousAssetException(String message) { super(message); }
    }

    static class AssetInsuranceConflictException extends RuntimeException {
        AssetInsuranceConflictException(String message) { super(message); }
    }

    static class InvalidDateException extends RuntimeException {
        InvalidDateException(String message) { super(message); }
    }

    static class InvalidAmountException extends RuntimeException {
        InvalidAmountException(String message) { super(message); }
    }
}
