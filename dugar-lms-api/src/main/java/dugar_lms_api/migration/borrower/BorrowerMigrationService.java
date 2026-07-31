package dugar_lms_api.migration.borrower;

import dugar_lms_api.migration.common.MigrationAuditConstants;
import dugar_lms_api.migration.common.MigrationRunService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class BorrowerMigrationService {

    static final String MAPPING_FILE_NAME = "Master data LMS.xlsx";
    static final String MAPPING_SHEET_NAME = "Borrower Details";
    static final String SOURCE_TABLE = "G_PARTYMAS_DATA_TABLE";
    static final String SOURCE_FILE_NAME = SOURCE_TABLE + ".xlsx";
    static final String SOURCE_SHEET_NAME = "G_PARTYMAS";
    static final String CREATED_BY = "LEGACY_MIGRATION";
    static final String TARGET_TABLE = "party_masters";

    private final BorrowerExcelReader borrowerExcelReader;
    private final BorrowerMigrationRepository borrowerMigrationRepository;
    private final MigrationRunService migrationRunService;
    private final TransactionTemplate rowTransactionTemplate;
    private final Path migrationDataDirectory;

    @Autowired
    public BorrowerMigrationService(
        BorrowerExcelReader borrowerExcelReader,
        BorrowerMigrationRepository borrowerMigrationRepository,
        MigrationRunService migrationRunService,
        PlatformTransactionManager transactionManager
    ) {
        this(
            borrowerExcelReader,
            borrowerMigrationRepository,
            migrationRunService,
            transactionManager,
            Path.of("migration_data")
        );
    }

    BorrowerMigrationService(
        BorrowerExcelReader borrowerExcelReader,
        BorrowerMigrationRepository borrowerMigrationRepository,
        MigrationRunService migrationRunService,
        PlatformTransactionManager transactionManager,
        Path migrationDataDirectory
    ) {
        this.borrowerExcelReader = borrowerExcelReader;
        this.borrowerMigrationRepository = borrowerMigrationRepository;
        this.migrationRunService = migrationRunService;
        this.migrationDataDirectory = migrationDataDirectory;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rowTransactionTemplate = transactionTemplate;
    }

    public BorrowerMigrationSummary importBorrowersAndGuarantors() {
        String sourceTable = discoverSourceTable();

        Path sourceFile = migrationDataDirectory.resolve(sourceTable + ".xlsx");
        if (!Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException("Source file not found: " + sourceTable + ".xlsx");
        }

        BorrowerExcelReader.ParsedBorrowerSheet parsedSheet = readSourceSheet(sourceFile);
        BorrowerMigrationSummary summary = baseSummary(parsedSheet);
        Long migrationRunId = migrationRunService.startRun(
            MigrationAuditConstants.MigrationTypes.BORROWER_GUARANTOR_MASTER,
            sourceFile.getFileName().toString(),
            SOURCE_SHEET_NAME,
            parsedSheet.rows().size(),
            CREATED_BY
        );
        summary.setMigrationRunId(migrationRunId);

        List<String> schemaIssues = validateRuntimeSchema();
        if (!schemaIssues.isEmpty()) {
            summary.setSuccess(false);
            summary.setTotalSourceRows(parsedSheet.rows().size());
            for (String issue : schemaIssues) {
                summary.incrementFailed();
                summary.addIssue(null, null, MigrationAuditConstants.DetailResultTypes.FAILED, issue);
                migrationRunService.recordFailure(migrationRunId, null, null, issue, null);
            }
            migrationRunService.failRun(migrationRunId, 0, 0, summary.getFailed(), "Schema validation failed");
            return summary;
        }

        for (BorrowerMigrationRow row : parsedSheet.rows()) {
            handleRow(migrationRunId, row, summary);
        }

        validateContractReferences(migrationRunId, summary);
        summary.setSuccess(summary.getFailed() == 0 && summary.getConflicts() == 0);
        migrationRunService.completeRun(
            migrationRunId,
            summary.getInserted(),
            summary.getDuplicates(),
            summary.getFailed() + summary.getConflicts(),
            finalRunStatus(summary)
        );
        return summary;
    }

    RowOutcome importRow(BorrowerMigrationRow row) {
        if (row.isBlank()) {
            return RowOutcome.SKIPPED_BLANK;
        }
        if (row.isRepeatedHeader()) {
            return RowOutcome.SKIPPED_HEADER;
        }

        PartyMasterRecord incoming = toParty(row);
        Optional<PartyMasterRecord> existing = borrowerMigrationRepository.findByPartyCode(incoming.partyCode());
        if (existing.isEmpty()) {
            borrowerMigrationRepository.insertParty(incoming, CREATED_BY);
            return RowOutcome.INSERTED;
        }
        if (incoming.hasSameMappedValues(existing.get())) {
            return RowOutcome.DUPLICATE;
        }
        throw new PartyConflictException("Existing party has different mapped values", incoming, existing.get());
    }

    PartyMasterRecord toParty(BorrowerMigrationRow row) {
        String partyCode = required(row.partyCode(), "PARTY_CODE");
        String fullName = required(row.fullName(), "PARTY_NAME1");
        PartyType partyType = PartyType.fromSourceCode(required(row.sourcePartyType(), "PARTY_TYPE"));

        return new PartyMasterRecord(
            partyCode,
            partyType,
            nullIfBlank(row.salutation()),
            fullName,
            nullIfBlank(row.swdName()),
            nullIfBlank(row.addressLine1()),
            nullIfBlank(row.addressLine2()),
            nullIfBlank(row.area()),
            nullIfBlank(row.city()),
            nullIfBlank(row.state()),
            nullIfBlank(row.pinCode()),
            nullIfBlank(row.contactNumber()),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true
        );
    }

    void validateContractReferences(Long migrationRunId, BorrowerMigrationSummary summary) {
        Set<String> borrowerCodes = cleanCodes(borrowerMigrationRepository.distinctContractBorrowerCodes());
        Set<String> guarantorCodes = cleanCodes(borrowerMigrationRepository.distinctContractGuarantorCodes());

        Set<String> matchedBorrowers = cleanCodes(borrowerMigrationRepository.existingPartyCodes(borrowerCodes, PartyType.BORROWER));
        Set<String> matchedGuarantors = cleanCodes(borrowerMigrationRepository.existingPartyCodes(guarantorCodes, PartyType.GUARANTOR));

        Set<String> missingBorrowers = difference(borrowerCodes, matchedBorrowers);
        Set<String> missingGuarantors = difference(guarantorCodes, matchedGuarantors);

        summary.setDistinctContractHirerCodes(borrowerCodes.size());
        summary.setDistinctContractGuarantorCodes(guarantorCodes.size());
        summary.setMatchedHirerCodes(matchedBorrowers.size());
        summary.setMatchedGuarantorCodes(matchedGuarantors.size());
        summary.setMissingHirerCodes(missingBorrowers.size());
        summary.setMissingGuarantorCodes(missingGuarantors.size());
        summary.setBlankHirerCodes(borrowerMigrationRepository.blankContractBorrowerCodeCount());
        summary.setBlankGuarantorCodes(borrowerMigrationRepository.blankContractGuarantorCodeCount());

        for (String missing : missingBorrowers) {
            migrationRunService.recordMissingReference(
                migrationRunId,
                missing,
                "Missing party master for contract borrower_code",
                "{\"contractColumn\":\"borrower_code\",\"partyCode\":\"" + missing + "\"}"
            );
        }
        for (String missing : missingGuarantors) {
            migrationRunService.recordMissingReference(
                migrationRunId,
                missing,
                "Missing party master for contract guarantor_code",
                "{\"contractColumn\":\"guarantor_code\",\"partyCode\":\"" + missing + "\"}"
            );
        }
    }

    private void handleRow(Long migrationRunId, BorrowerMigrationRow row, BorrowerMigrationSummary summary) {
        try {
            RowOutcome outcome = rowTransactionTemplate.execute(status -> importRow(row));
            if (outcome == RowOutcome.SKIPPED_BLANK) {
                summary.incrementSkippedBlankRows();
                return;
            }
            if (outcome == RowOutcome.SKIPPED_HEADER) {
                summary.incrementRepeatedHeaderRows();
                return;
            }

            summary.incrementEligibleRows();
            PartyType partyType = PartyType.fromSourceCode(row.sourcePartyType());
            if (partyType == PartyType.BORROWER) {
                summary.incrementBorrowerRows();
            } else {
                summary.incrementGuarantorRows();
            }

            if (outcome == RowOutcome.INSERTED) {
                summary.incrementInserted();
            } else {
                summary.incrementDuplicates();
                summary.addIssue(row.excelRow(), row.partyCode(), MigrationAuditConstants.DetailResultTypes.DUPLICATE, "Identical party already exists");
                migrationRunService.recordDuplicate(migrationRunId, row.excelRow(), row.partyCode(), "Identical party already exists", sourceData(row));
            }
        } catch (PartyConflictException exception) {
            summary.incrementEligibleRows();
            countClassifiedRow(row, summary);
            summary.incrementConflicts();
            summary.addIssue(row.excelRow(), row.partyCode(), MigrationAuditConstants.DetailResultTypes.CONFLICT, exception.getMessage());
            migrationRunService.recordConflict(migrationRunId, row.excelRow(), row.partyCode(), exception.getMessage(), exception.sourceData());
        } catch (Exception exception) {
            String reason = rootMessage(exception);
            summary.incrementFailed();
            if (reason.startsWith("Invalid party type")) {
                summary.incrementInvalidClassificationRows();
            }
            summary.addIssue(row.excelRow(), row.partyCode(), MigrationAuditConstants.DetailResultTypes.FAILED, reason);
            migrationRunService.recordFailure(migrationRunId, row.excelRow(), row.partyCode(), reason, sourceData(row));
        }
    }

    private void countClassifiedRow(BorrowerMigrationRow row, BorrowerMigrationSummary summary) {
        try {
            PartyType partyType = PartyType.fromSourceCode(row.sourcePartyType());
            if (partyType == PartyType.BORROWER) {
                summary.incrementBorrowerRows();
            } else {
                summary.incrementGuarantorRows();
            }
        } catch (IllegalArgumentException ignored) {
            summary.incrementInvalidClassificationRows();
        }
    }

    private BorrowerExcelReader.ParsedBorrowerSheet readSourceSheet(Path sourceFile) {
        try (InputStream inputStream = Files.newInputStream(sourceFile)) {
            return borrowerExcelReader.read(inputStream, SOURCE_SHEET_NAME);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read source file: " + SOURCE_FILE_NAME, exception);
        }
    }

    private String discoverSourceTable() {
        Path mappingFile = migrationDataDirectory.resolve(MAPPING_FILE_NAME);
        if (!Files.isRegularFile(mappingFile)) {
            throw new IllegalArgumentException("Mapping file not found: " + MAPPING_FILE_NAME);
        }

        try (InputStream inputStream = Files.newInputStream(mappingFile);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(MAPPING_SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("Mapping sheet not found: " + MAPPING_SHEET_NAME);
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                for (int cellIndex = row.getFirstCellNum(); cellIndex >= 0 && cellIndex < row.getLastCellNum(); cellIndex++) {
                    String value = formatter.formatCellValue(row.getCell(cellIndex), evaluator).trim();
                    if (SOURCE_TABLE.equalsIgnoreCase(value)) {
                        return SOURCE_TABLE;
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read mapping file: " + MAPPING_FILE_NAME, exception);
        }

        throw new IllegalArgumentException("Borrower mapping does not reference source table: " + SOURCE_TABLE);
    }

    private BorrowerMigrationSummary baseSummary(BorrowerExcelReader.ParsedBorrowerSheet parsedSheet) {
        BorrowerMigrationSummary summary = new BorrowerMigrationSummary();
        summary.setMappingSheetInspected(MAPPING_FILE_NAME + " / " + MAPPING_SHEET_NAME);
        summary.setOracleSourceTable(SOURCE_TABLE);
        summary.setSourceFileName(SOURCE_FILE_NAME);
        summary.setSourceSheetName(SOURCE_SHEET_NAME);
        summary.setDetectedSourceHeaders(parsedSheet.headers());
        summary.setPartyCodeColumn("PARTY_CODE");
        summary.setPartyTypeColumn("PARTY_TYPE");
        summary.setTargetTable(TARGET_TABLE);
        summary.setTotalSourceRows(parsedSheet.rows().size());
        return summary;
    }

    private List<String> validateRuntimeSchema() {
        Set<String> columns = borrowerMigrationRepository.tableColumns(TARGET_TABLE);
        Set<String> required = Set.of(
            "party_code",
            "party_type",
            "salutation",
            "full_name",
            "swd_name",
            "address_line_1",
            "address_line_2",
            "area",
            "city",
            "state",
            "pin_code",
            "contact_number",
            "is_active",
            "created_at",
            "updated_at"
        );

        return required.stream()
            .filter(column -> !columns.contains(column))
            .map(column -> "Schema mismatch: missing column " + TARGET_TABLE + "." + column)
            .toList();
    }

    private Set<String> cleanCodes(Set<String> codes) {
        Set<String> cleaned = new LinkedHashSet<>();
        for (String code : codes) {
            String normalized = nullIfBlank(code);
            if (normalized != null) {
                cleaned.add(normalized.toUpperCase(Locale.ROOT));
            }
        }
        return cleaned;
    }

    private Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private String required(String value, String header) {
        String normalized = nullIfBlank(value);
        if (normalized == null) {
            throw new IllegalArgumentException(header + " is required");
        }
        return normalized;
    }

    private String nullIfBlank(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sourceData(BorrowerMigrationRow row) {
        return "{\"partyCode\":\"" + safe(row.partyCode()) + "\",\"partyType\":\"" + safe(row.sourcePartyType()) + "\"}";
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String finalRunStatus(BorrowerMigrationSummary summary) {
        if (summary.getFailed() == 0 && summary.getConflicts() == 0) {
            return MigrationAuditConstants.RunStatuses.COMPLETED;
        }
        if (summary.getInserted() > 0) {
            return MigrationAuditConstants.RunStatuses.PARTIALLY_COMPLETED;
        }
        return MigrationAuditConstants.RunStatuses.FAILED;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Row import failed" : current.getMessage();
    }

    enum RowOutcome {
        INSERTED,
        DUPLICATE,
        SKIPPED_BLANK,
        SKIPPED_HEADER
    }

    static class PartyConflictException extends RuntimeException {
        private final PartyMasterRecord incoming;
        private final PartyMasterRecord existing;

        PartyConflictException(String message, PartyMasterRecord incoming, PartyMasterRecord existing) {
            super(message);
            this.incoming = incoming;
            this.existing = existing;
        }

        String sourceData() {
            return "{\"existing\":\"" + existing + "\",\"incoming\":\"" + incoming + "\"}";
        }
    }
}
