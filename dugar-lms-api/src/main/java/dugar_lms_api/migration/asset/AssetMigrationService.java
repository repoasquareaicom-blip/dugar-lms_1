package dugar_lms_api.migration.asset;

import dugar_lms_api.migration.common.MigrationAuditConstants;
import dugar_lms_api.migration.common.MigrationRunService;
import dugar_lms_api.modules.assets.dto.AssetCreateDto;
import dugar_lms_api.modules.assets.dto.AssetDto;
import dugar_lms_api.modules.assets.repository.AssetRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AssetMigrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetMigrationService.class);

    static final String MAPPING_FILE_NAME = "Master data LMS.xlsx";
    static final String MAPPING_SHEET_NAME = "Assets Details";
    static final String PRIMARY_SOURCE_TABLE = "HP_CONTR_DETAILS";
    static final String PRIMARY_SOURCE_FILE_NAME = "HP_CONTR_DETAILS_DATA_TABLE.xlsx";
    static final String PRIMARY_SOURCE_SHEET_NAME = "HP_CONTR_DETAILS";
    static final String CONTRACT_SOURCE_FILE_NAME = "HP_CONTR_DATA_TABLE.xlsx";
    static final String CONTRACT_SOURCE_SHEET_NAME = "HP_CONTR";
    static final String TARGET_TABLE = "assets";
    static final String CREATED_BY = "LEGACY_MIGRATION";

    private static final String MISSING_CONTRACT = "MISSING_CONTRACT";
    private static final String DUPLICATE_SOURCE_CONTRACT = "DUPLICATE_SOURCE_CONTRACT";

    private final AssetMigrationExcelReader assetMigrationExcelReader;
    private final AssetRepository assetRepository;
    private final MigrationRunService migrationRunService;
    private final TransactionTemplate rowTransactionTemplate;
    private final Path migrationDataDirectory;

    @Autowired
    public AssetMigrationService(
        AssetMigrationExcelReader assetMigrationExcelReader,
        AssetRepository assetRepository,
        MigrationRunService migrationRunService,
        PlatformTransactionManager transactionManager
    ) {
        this(
            assetMigrationExcelReader,
            assetRepository,
            migrationRunService,
            transactionManager,
            Path.of("migration_data")
        );
    }

    AssetMigrationService(
        AssetMigrationExcelReader assetMigrationExcelReader,
        AssetRepository assetRepository,
        MigrationRunService migrationRunService,
        PlatformTransactionManager transactionManager,
        Path migrationDataDirectory
    ) {
        this.assetMigrationExcelReader = assetMigrationExcelReader;
        this.assetRepository = assetRepository;
        this.migrationRunService = migrationRunService;
        this.migrationDataDirectory = migrationDataDirectory;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rowTransactionTemplate = transactionTemplate;
    }

    public AssetMigrationSummary importAssets() {
        Path detailsFile = migrationDataDirectory.resolve(PRIMARY_SOURCE_FILE_NAME);
        Path contractFile = migrationDataDirectory.resolve(CONTRACT_SOURCE_FILE_NAME);
        requireFile(detailsFile, PRIMARY_SOURCE_FILE_NAME);
        requireFile(contractFile, CONTRACT_SOURCE_FILE_NAME);

        List<AssetMigrationRow> rows = readDetails(detailsFile);
        Map<String, ContractAssetSupplement> contractSupplements = readContractSupplements(contractFile);
        Map<String, Integer> contractKeyCounts = normalizedContractKeyCounts(rows);

        AssetMigrationSummary summary = AssetMigrationSummary.base();
        summary.setTotalSourceRows(rows.size());
        Long migrationRunId = migrationRunService.startRun(
            MigrationAuditConstants.MigrationTypes.ASSET,
            PRIMARY_SOURCE_FILE_NAME,
            PRIMARY_SOURCE_SHEET_NAME,
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

        for (AssetMigrationRow row : rows) {
            handleRow(migrationRunId, row, contractSupplements, contractKeyCounts, summary);
        }

        logSummary("Before completeProcessStatus", summary);
        summary.completeProcessStatus();
        logSummary("After completeProcessStatus", summary);
        migrationRunService.completeRun(
            migrationRunId,
            summary.getInserted(),
            summary.getDuplicates(),
            summary.getFailed() + summary.getConflicts() + summary.getMissingContracts(),
            finalRunStatus(summary)
        );
        logSummary("Before returning from AssetMigrationService#importAssets", summary);
        return summary;
    }

    public AssetMigrationSummary prepareAssetMigration() {
        AssetMigrationSummary summary = AssetMigrationSummary.base();
        List<String> schemaIssues = validateRuntimeSchema();
        if (!schemaIssues.isEmpty()) {
            summary.setSuccess(false);
            schemaIssues.forEach(issue -> summary.addIssue(null, null, MigrationAuditConstants.DetailResultTypes.FAILED, issue));
        }
        return summary;
    }

    RowOutcome importRow(
        AssetMigrationRow row,
        Map<String, ContractAssetSupplement> contractSupplements,
        Map<String, Integer> contractKeyCounts
    ) {
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

        String referenceKey = referenceKey(contractType, contractNumber);
        if (contractKeyCounts.getOrDefault(referenceKey, 0) > 1) {
            throw new DuplicateSourceContractException("Duplicate HP_CONTR_DETAILS rows for " + referenceKey);
        }

        Optional<Long> contractId = assetRepository.findContractId(contractType, contractNumber);
        if (contractId.isEmpty()) {
            throw new MissingContractException("Missing contract for " + referenceKey);
        }

        AssetMigrationRecord incoming = toRecord(row, contractId.get(), contractSupplements.get(referenceKey));
        BusinessKey businessKey = businessKey(incoming);
        List<AssetDto> existingAssets = assetRepository.findByBusinessKey(contractId.get(), businessKey.type(), businessKey.value());
        if (!existingAssets.isEmpty()) {
            AssetDto existing = existingAssets.getFirst();
            List<String> differences = differences(existing, incoming);
            if (differences.isEmpty()) {
                return RowOutcome.DUPLICATE;
            }
            throw new AssetConflictException("Existing asset has different mapped values: " + String.join(", ", differences));
        }

        assetRepository.insert(new AssetCreateDto(
            incoming.contractId(),
            incoming.oracleContractType(),
            incoming.oracleContractNo(),
            incoming.financeType(),
            incoming.vehicleTypeCode(),
            incoming.registrationNumber(),
            incoming.engineNumber(),
            incoming.chassisNumber(),
            incoming.manufactureYear(),
            incoming.equipmentValue(),
            incoming.securityOffered(),
            incoming.ownerSerialNo(),
            incoming.sourceTable(),
            incoming.sourceRowHash()
        ));
        return RowOutcome.INSERTED;
    }

    AssetMigrationRecord toRecord(AssetMigrationRow row, Long contractId, ContractAssetSupplement supplement) {
        String contractType = normalizeContract(row.contractType());
        String contractNumber = normalizeContract(row.contractNumber());
        String registrationNumber = nullIfPlaceholder(supplement == null ? null : supplement.registrationNumber());
        String engineNumber = nullIfPlaceholder(row.engineNumber());
        String chassisNumber = nullIfPlaceholder(row.chassisNumber());

        return new AssetMigrationRecord(
            contractId,
            contractType,
            contractNumber,
            contractType,
            nullIfPlaceholder(row.vehicleTypeCode()),
            registrationNumber,
            normalizeRegistration(registrationNumber),
            engineNumber,
            normalizeIdentifier(engineNumber),
            chassisNumber,
            normalizeIdentifier(chassisNumber),
            nullIfPlaceholder(supplement == null ? null : supplement.manufactureYear()),
            row.equipmentValue(),
            nullIfPlaceholder(row.securityOffered()),
            nullIfPlaceholder(row.ownerSerialNo()),
            PRIMARY_SOURCE_TABLE,
            sourceRowHash(row, registrationNumber)
        );
    }

    void countBlankIdentifiers(AssetMigrationRow row, ContractAssetSupplement supplement, AssetMigrationSummary summary) {
        if (normalizeRegistration(supplement == null ? null : supplement.registrationNumber()) == null) {
            summary.incrementBlankRegistrationNumbers();
        }
        if (normalizeIdentifier(row.engineNumber()) == null) {
            summary.incrementBlankEngineNumbers();
        }
        if (normalizeIdentifier(row.chassisNumber()) == null) {
            summary.incrementBlankChassisNumbers();
        }
    }

    BusinessKey businessKey(AssetMigrationRecord record) {
        if (record.normalizedChassisNumber() != null) {
            return new BusinessKey("CHASSIS", record.normalizedChassisNumber());
        }
        if (record.normalizedEngineNumber() != null) {
            return new BusinessKey("ENGINE", record.normalizedEngineNumber());
        }
        if (record.normalizedRegistrationNumber() != null) {
            return new BusinessKey("REGISTRATION", record.normalizedRegistrationNumber());
        }
        return new BusinessKey("SOURCE_ROW_HASH", record.sourceRowHash());
    }

    String normalizeContract(String value) {
        String normalized = nullIfPlaceholder(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    String normalizeIdentifier(String value) {
        String normalized = nullIfPlaceholder(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    String normalizeRegistration(String value) {
        String normalized = normalizeIdentifier(value);
        return normalized == null ? null : normalized.replace(" ", "").replace("-", "");
    }

    private void handleRow(
        Long migrationRunId,
        AssetMigrationRow row,
        Map<String, ContractAssetSupplement> contractSupplements,
        Map<String, Integer> contractKeyCounts,
        AssetMigrationSummary summary
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
            String contractType = normalizeContract(row.contractType());
            String contractNumber = normalizeContract(row.contractNumber());
            String referenceKey = referenceKey(contractType, contractNumber);
            countBlankIdentifiers(row, contractSupplements.get(referenceKey), summary);

            RowOutcome outcome = rowTransactionTemplate.execute(status -> importRow(row, contractSupplements, contractKeyCounts));
            if (outcome == RowOutcome.INSERTED) {
                summary.incrementInserted();
            } else if (outcome == RowOutcome.DUPLICATE) {
                summary.incrementDuplicates();
                summary.addIssue(row.excelRow(), referenceKey, MigrationAuditConstants.DetailResultTypes.DUPLICATE, "Identical asset already exists");
                migrationRunService.recordDuplicate(migrationRunId, row.excelRow(), referenceKey, "Identical asset already exists", sourceData(row));
            }
        } catch (MissingContractException exception) {
            String referenceKey = safeReferenceKey(row);
            summary.incrementMissingContracts();
            summary.addIssue(row.excelRow(), referenceKey, MISSING_CONTRACT, exception.getMessage());
            migrationRunService.recordMissingReference(migrationRunId, referenceKey, exception.getMessage(), sourceData(row));
        } catch (DuplicateSourceContractException exception) {
            String referenceKey = safeReferenceKey(row);
            summary.incrementConflicts();
            summary.addIssue(row.excelRow(), referenceKey, DUPLICATE_SOURCE_CONTRACT, exception.getMessage());
            migrationRunService.recordConflict(migrationRunId, row.excelRow(), referenceKey, exception.getMessage(), sourceData(row));
        } catch (AssetConflictException exception) {
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

    private Map<String, Integer> normalizedContractKeyCounts(List<AssetMigrationRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AssetMigrationRow row : rows) {
            if (row.isBlank() || row.isRepeatedHeader()) {
                continue;
            }
            String contractType = normalizeContract(row.contractType());
            String contractNumber = normalizeContract(row.contractNumber());
            if (contractType != null && contractNumber != null) {
                counts.merge(referenceKey(contractType, contractNumber), 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<String, ContractAssetSupplement> readContractSupplements(Path contractFile) {
        List<ContractAssetSupplement> rows;
        try (InputStream inputStream = Files.newInputStream(contractFile)) {
            rows = assetMigrationExcelReader.readContractSupplements(inputStream, CONTRACT_SOURCE_SHEET_NAME);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read source file: " + CONTRACT_SOURCE_FILE_NAME, exception);
        }

        Map<String, List<ContractAssetSupplement>> grouped = new LinkedHashMap<>();
        for (ContractAssetSupplement row : rows) {
            String contractType = normalizeContract(row.contractType());
            String contractNumber = normalizeContract(row.contractNumber());
            if (contractType != null && contractNumber != null) {
                grouped.computeIfAbsent(referenceKey(contractType, contractNumber), ignored -> new ArrayList<>()).add(row);
            }
        }

        Map<String, ContractAssetSupplement> supplements = new LinkedHashMap<>();
        for (Map.Entry<String, List<ContractAssetSupplement>> entry : grouped.entrySet()) {
            if (entry.getValue().size() == 1) {
                supplements.put(entry.getKey(), entry.getValue().getFirst());
            }
        }
        return supplements;
    }

    private List<AssetMigrationRow> readDetails(Path detailsFile) {
        try (InputStream inputStream = Files.newInputStream(detailsFile)) {
            return assetMigrationExcelReader.readDetails(inputStream, PRIMARY_SOURCE_SHEET_NAME);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read source file: " + PRIMARY_SOURCE_FILE_NAME, exception);
        }
    }

    private List<String> validateRuntimeSchema() {
        Set<String> columns = assetRepository.tableColumns(TARGET_TABLE);
        Set<String> required = Set.of(
            "asset_id",
            "contract_id",
            "oracle_contract_type",
            "oracle_contract_no",
            "finance_type",
            "vehicle_type_code",
            "registration_number",
            "engine_number",
            "chassis_number",
            "manufacture_year",
            "equipment_value",
            "security_offered",
            "owner_serial_no",
            "source_table",
            "source_row_hash",
            "created_at",
            "updated_at"
        );

        return required.stream()
            .filter(column -> !columns.contains(column))
            .map(column -> "Schema mismatch: missing column " + TARGET_TABLE + "." + column)
            .toList();
    }

    private List<String> differences(AssetDto existing, AssetMigrationRecord incoming) {
        List<String> differences = new ArrayList<>();
        compare(differences, "oracle_contract_type", existing.oracleContractType(), incoming.oracleContractType());
        compare(differences, "oracle_contract_no", existing.oracleContractNo(), incoming.oracleContractNo());
        compare(differences, "finance_type", existing.financeType(), incoming.financeType());
        compare(differences, "vehicle_type_code", existing.vehicleTypeCode(), incoming.vehicleTypeCode());
        compare(differences, "registration_number", normalizeRegistration(existing.registrationNumber()), incoming.normalizedRegistrationNumber());
        compare(differences, "engine_number", normalizeIdentifier(existing.engineNumber()), incoming.normalizedEngineNumber());
        compare(differences, "chassis_number", normalizeIdentifier(existing.chassisNumber()), incoming.normalizedChassisNumber());
        compare(differences, "manufacture_year", existing.manufactureYear(), incoming.manufactureYear());
        compare(differences, "equipment_value", existing.equipmentValue(), incoming.equipmentValue());
        compare(differences, "security_offered", existing.securityOffered(), incoming.securityOffered());
        compare(differences, "owner_serial_no", existing.ownerSerialNo(), incoming.ownerSerialNo());
        return differences;
    }

    private void compare(List<String> differences, String field, Object existing, Object incoming) {
        if (existing instanceof String existingText && incoming instanceof String incomingText) {
            if (!existingText.trim().equalsIgnoreCase(incomingText.trim())) {
                differences.add(field);
            }
            return;
        }
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

    private String nullIfPlaceholder(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (Set.of(".", "..", "NULL", "NIL", "NA", "N/A", "NONE", "-").contains(upper)) {
            return null;
        }
        return normalized;
    }

    private String sourceRowHash(AssetMigrationRow row, String registrationNumber) {
        String payload = String.join("|",
            safe(normalizeContract(row.contractType())),
            safe(normalizeContract(row.contractNumber())),
            safe(nullIfPlaceholder(row.vehicleTypeCode())),
            safe(row.equipmentValue() == null ? null : row.equipmentValue().stripTrailingZeros().toPlainString()),
            safe(nullIfPlaceholder(row.securityOffered())),
            safe(normalizeIdentifier(row.engineNumber())),
            safe(normalizeIdentifier(row.chassisNumber())),
            safe(normalizeRegistration(registrationNumber)),
            safe(nullIfPlaceholder(row.ownerSerialNo()))
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

    private String sourceData(AssetMigrationRow row) {
        return "{\"contractType\":\"" + safe(row.contractType()) + "\",\"contractNumber\":\"" + safe(row.contractNumber())
            + "\",\"engineNumber\":\"" + safe(row.engineNumber()) + "\",\"chassisNumber\":\"" + safe(row.chassisNumber()) + "\"}";
    }

    private void requireFile(Path path, String fileName) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Source file not found: " + fileName);
        }
    }

    private String finalRunStatus(AssetMigrationSummary summary) {
        if (summary.getFailed() == 0) {
            return MigrationAuditConstants.RunStatuses.COMPLETED;
        }
        if (summary.getInserted() > 0) {
            return MigrationAuditConstants.RunStatuses.PARTIALLY_COMPLETED;
        }
        return MigrationAuditConstants.RunStatuses.FAILED;
    }

    private String safeReferenceKey(AssetMigrationRow row) {
        return referenceKey(normalizeContract(row.contractType()), normalizeContract(row.contractNumber()));
    }

    private String referenceKey(String contractType, String contractNumber) {
        return safe(contractType) + "/" + safe(contractNumber);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Asset import failed" : current.getMessage();
    }

    private void logSummary(String message, AssetMigrationSummary summary) {
        LOGGER.info(
            "{} summaryId={} success={} failed={} duplicates={} missingContracts={} completedWithWarnings={}",
            message,
            System.identityHashCode(summary),
            summary.isSuccess(),
            summary.getFailed(),
            summary.getDuplicates(),
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

    record BusinessKey(String type, String value) {
    }

    static class MissingContractException extends RuntimeException {
        MissingContractException(String message) {
            super(message);
        }
    }

    static class DuplicateSourceContractException extends RuntimeException {
        DuplicateSourceContractException(String message) {
            super(message);
        }
    }

    static class AssetConflictException extends RuntimeException {
        AssetConflictException(String message) {
            super(message);
        }
    }
}
