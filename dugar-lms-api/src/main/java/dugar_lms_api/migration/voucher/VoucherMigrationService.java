package dugar_lms_api.migration.voucher;

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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class VoucherMigrationService {

    static final String HEADER_FILE_NAME = "HP_VRHDR_DATA_TABLE.xlsx";
    static final String HEADER_SHEET_NAME = "HP_VRHDR";
    static final String DETAIL_FILE_NAME = "HP_VRDTL_DATA_TABLE.xlsx";
    static final String DETAIL_SHEET_NAME = "HP_VRDTL";
    static final String LEDGER_FILE_NAME = "ACCMAS_DATA_TABLE.xlsx";
    static final String LEDGER_SHEET_NAME = "ACCMAS";
    static final String PARTY_FILE_NAME = "PARTYMAS_DATA_TABLE.xlsx";
    static final String PARTY_SHEET_NAME = "PARTYMAS";
    static final String MAPPING_FILE_NAME = "Voucher data.xlsx";
    static final String MIGRATION_TYPE = "VOUCHER";

    private static final Logger LOGGER = LoggerFactory.getLogger(VoucherMigrationService.class);
    private static final String CREATED_BY = "LEGACY_MIGRATION";
    private static final String WARNING = "WARNING";
    private static final String DUPLICATE = MigrationAuditConstants.DetailResultTypes.DUPLICATE;
    private static final String FAILED = MigrationAuditConstants.DetailResultTypes.FAILED;

    private static final Set<String> HEADER_REQUIRED_HEADERS = Set.of(
        "VOUCHER_TYPE", "VOUCHER_NO", "VOUCHER_DATE", "STAMP_DATE", "VR_AMT", "CONT_NO", "CONT_TYPE",
        "PRNO", "TR_NO", "TR_DT", "BANK_CODE", "CHEQ_NO", "HDR_AC_CD"
    );
    private static final Set<String> DETAIL_REQUIRED_HEADERS = Set.of(
        "VOUCHER_TYPE", "VOUCHER_NO", "SL_NO", "CATEGORY", "AC_CD", "AC_SBCD", "NARR_1",
        "DEBIT_AMT", "CREDIT_AMT", "PARTY_NAME", "PTY_ADDR1", "PTY_ADDR2", "PTY_ADDR3", "PTY_CITY", "PTY_PIN", "PTY_PH"
    );
    private static final Set<String> LEDGER_REQUIRED_HEADERS = Set.of("AC_CD", "AC_DESC");
    private static final Set<String> PARTY_REQUIRED_HEADERS = Set.of("AC_CD", "PARTY_CODE", "PARTY_NAME", "PTY_ADDR1", "PTY_ADDR2", "PTY_ADDR3", "PTY_CITY", "PTY_PIN");

    private final VoucherExcelReader excelReader;
    private final VoucherMigrationRepository repository;
    private final MigrationRunService migrationRunService;
    private final TransactionTemplate rowTransactionTemplate;
    private final Path migrationDataDirectory;

    @Autowired
    public VoucherMigrationService(
        VoucherExcelReader excelReader,
        VoucherMigrationRepository repository,
        MigrationRunService migrationRunService,
        PlatformTransactionManager transactionManager
    ) {
        this(excelReader, repository, migrationRunService, transactionManager, Path.of("migration_data"));
    }

    VoucherMigrationService(
        VoucherExcelReader excelReader,
        VoucherMigrationRepository repository,
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

    public VoucherMigrationSummary prepareVoucherMigration() {
        VoucherContext context = loadContext();
        VoucherMigrationSummary summary = baseSummary(context);
        validateSchemaForSummary(summary);
        if (!summary.isSuccess()) {
            summary.completeProcessStatus();
            return summary;
        }

        for (VoucherSourceRow header : context.headers()) {
            handleHeader(null, header, context, summary, false);
        }
        summary.completeProcessStatus();
        return summary;
    }

    public VoucherMigrationSummary importVouchers() {
        VoucherContext context = loadContext();
        VoucherMigrationSummary summary = baseSummary(context);
        Long migrationRunId = migrationRunService.startRun(
            MIGRATION_TYPE,
            HEADER_FILE_NAME + ", " + DETAIL_FILE_NAME,
            HEADER_SHEET_NAME + ", " + DETAIL_SHEET_NAME,
            summary.getTotalSourceRows(),
            CREATED_BY
        );
        summary.setMigrationRunId(migrationRunId);

        List<String> schemaIssues = validateRuntimeSchema();
        if (!schemaIssues.isEmpty()) {
            summary.setSuccess(false);
            summary.setFailed(summary.getTotalSourceRows());
            for (String issue : schemaIssues) {
                summary.addIssue(null, null, FAILED, issue);
                migrationRunService.recordFailure(migrationRunId, null, null, issue, null);
            }
            migrationRunService.failRun(migrationRunId, 0, 0, summary.getFailed(), "Schema validation failed");
            return summary;
        }

        for (VoucherSourceRow header : context.headers()) {
            handleHeader(migrationRunId, header, context, summary, true);
        }

        summary.completeProcessStatus();
        migrationRunService.completeRun(
            migrationRunId,
            summary.getInserted(),
            summary.getDuplicates(),
            summary.getFailed(),
            finalRunStatus(summary)
        );
        return summary;
    }

    RowOutcome importHeader(VoucherSourceRow header, VoucherContext context, VoucherMigrationSummary summary, Long migrationRunId, boolean insert) {
        String voucherType = requiredText(header, "VOUCHER_TYPE");
        String voucherNumber = requiredText(header, "VOUCHER_NO");
        String referenceKey = referenceKey(voucherType, voucherNumber);

        if (repository.voucherHeaderExists(voucherType, voucherNumber)) {
            return RowOutcome.DUPLICATE;
        }

        String contractType = text(header.value("CONT_TYPE"));
        String contractNumber = text(header.value("CONT_NO"));
        Long contractId = null;
        if (contractType != null && contractNumber != null) {
            List<Long> contractIds = repository.findContractIds(contractType, contractNumber);
            if (contractIds.isEmpty()) {
                warning(summary, migrationRunId, header.excelRow(), referenceKey, "Missing contract for " + contractType + "/" + contractNumber);
                summary.incrementMissingContracts();
            } else if (contractIds.size() > 1) {
                warning(summary, migrationRunId, header.excelRow(), referenceKey, "Multiple contracts found for " + contractType + "/" + contractNumber + "; contract_id stored as NULL");
                summary.incrementMissingContracts();
            } else {
                contractId = contractIds.getFirst();
            }
        }

        String headerControlCode = text(header.value("HDR_AC_CD"));
        String headerControlName = ledgerName(context.ledgers(), headerControlCode, summary, migrationRunId, header.excelRow(), referenceKey);

        Long voucherHeaderId = null;
        if (insert) {
            voucherHeaderId = repository.insertHeader(new VoucherMigrationRepository.VoucherHeaderInsert(
                voucherType,
                voucherTypeDescription(voucherType),
                voucherNumber,
                date(header.value("VOUCHER_DATE")),
                date(header.value("STAMP_DATE")),
                text(header.value("CHEQ_NO")),
                decimalOrWarn(header.value("VR_AMT"), "VR_AMT", summary, migrationRunId, header.excelRow(), referenceKey),
                text(header.value("PRNO")),
                text(header.value("TR_NO")),
                date(header.value("TR_DT")),
                contractNumber,
                contractType,
                contractId,
                text(header.value("BANK_CODE")),
                headerControlCode,
                headerControlName,
                null
            ));
        }

        for (VoucherSourceRow detail : context.detailsByVoucher().getOrDefault(referenceKey, List.of())) {
            importDetail(detail, voucherHeaderId, context, summary, migrationRunId, insert);
        }

        return RowOutcome.INSERTED;
    }

    private void importDetail(
        VoucherSourceRow detail,
        Long voucherHeaderId,
        VoucherContext context,
        VoucherMigrationSummary summary,
        Long migrationRunId,
        boolean insert
    ) {
        String referenceKey = referenceKey(text(detail.value("VOUCHER_TYPE")), text(detail.value("VOUCHER_NO")));
        Integer serialNumber = integerOrWarn(detail.value("SL_NO"), "SL_NO", summary, migrationRunId, detail.excelRow(), referenceKey);
        if (serialNumber == null) {
            summary.incrementFailed();
            return;
        }
        if (insert && repository.voucherDetailExists(voucherHeaderId, serialNumber)) {
            summary.incrementDuplicates();
            audit(summary, migrationRunId, detail.excelRow(), referenceKey + "/" + serialNumber, DUPLICATE, "Duplicate voucher detail already exists");
            return;
        }

        String ledgerCode = text(detail.value("AC_CD"));
        String ledgerName = ledgerName(context.ledgers(), ledgerCode, summary, migrationRunId, detail.excelRow(), referenceKey);
        String partyCode = text(detail.value("AC_SBCD"));
        PartyLookup party = party(context.parties(), ledgerCode, partyCode);
        if (partyCode != null && party == null) {
            warning(summary, migrationRunId, detail.excelRow(), referenceKey, "Missing party for ledger_code=" + safe(ledgerCode) + ", party_code=" + partyCode);
            summary.incrementMissingParties();
        }

        if (insert) {
            repository.insertDetail(new VoucherMigrationRepository.VoucherDetailInsert(
                voucherHeaderId,
                serialNumber,
                text(detail.value("CATEGORY")),
                ledgerCode,
                ledgerName,
                partyCode,
                decimalOrWarn(detail.value("DEBIT_AMT"), "DEBIT_AMT", summary, migrationRunId, detail.excelRow(), referenceKey),
                decimalOrWarn(detail.value("CREDIT_AMT"), "CREDIT_AMT", summary, migrationRunId, detail.excelRow(), referenceKey),
                partyCode,
                party == null ? text(detail.value("PARTY_NAME")) : party.partyName(),
                partyCode,
                narration(detail),
                party == null ? address(detail) : party.address()
            ));
        }
    }

    private void handleHeader(Long migrationRunId, VoucherSourceRow header, VoucherContext context, VoucherMigrationSummary summary, boolean insert) {
        if (header.isBlank()) {
            return;
        }
        try {
            RowOutcome outcome = rowTransactionTemplate.execute(status -> importHeader(header, context, summary, migrationRunId, insert));
            if (outcome == RowOutcome.DUPLICATE) {
                summary.incrementDuplicates();
                audit(summary, migrationRunId, header.excelRow(), referenceKey(text(header.value("VOUCHER_TYPE")), text(header.value("VOUCHER_NO"))), DUPLICATE, "Duplicate voucher already exists");
            } else if (insert) {
                summary.incrementInserted();
            }
        } catch (Exception exception) {
            LOGGER.error("Voucher row failed", exception);
            summary.incrementFailed();
            audit(summary, migrationRunId, header.excelRow(), referenceKey(text(header.value("VOUCHER_TYPE")), text(header.value("VOUCHER_NO"))), FAILED, rootMessage(exception));
        }
    }

    private VoucherContext loadContext() {
        Path headerFile = requireFile(HEADER_FILE_NAME);
        Path detailFile = requireFile(DETAIL_FILE_NAME);
        Path ledgerFile = requireFile(LEDGER_FILE_NAME);
        Path partyFile = requireFile(PARTY_FILE_NAME);
        requireFile(MAPPING_FILE_NAME);

        List<VoucherSourceRow> headers = readRows(headerFile, HEADER_SHEET_NAME, HEADER_REQUIRED_HEADERS).stream().filter(row -> !row.isBlank()).toList();
        List<VoucherSourceRow> details = readRows(detailFile, DETAIL_SHEET_NAME, DETAIL_REQUIRED_HEADERS).stream().filter(row -> !row.isBlank()).toList();
        Map<String, String> ledgers = ledgers(readRows(ledgerFile, LEDGER_SHEET_NAME, LEDGER_REQUIRED_HEADERS));
        Map<String, PartyLookup> parties = parties(readRows(partyFile, PARTY_SHEET_NAME, PARTY_REQUIRED_HEADERS));
        Map<String, List<VoucherSourceRow>> detailsByVoucher = details.stream()
            .collect(java.util.stream.Collectors.groupingBy(row -> referenceKey(text(row.value("VOUCHER_TYPE")), text(row.value("VOUCHER_NO"))), LinkedHashMap::new, java.util.stream.Collectors.toList()));

        return new VoucherContext(headers, details, detailsByVoucher, ledgers, parties);
    }

    private VoucherMigrationSummary baseSummary(VoucherContext context) {
        VoucherMigrationSummary summary = VoucherMigrationSummary.base();
        summary.setHeaderRows(context.headers().size());
        summary.setDetailRows(context.details().size());
        summary.setTotalSourceRows(context.headers().size());
        return summary;
    }

    private Path requireFile(String fileName) {
        Path file = migrationDataDirectory.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Missing source file: " + fileName);
        }
        return file;
    }

    private List<VoucherSourceRow> readRows(Path file, String sheetName, Set<String> headers) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            return excelReader.read(inputStream, sheetName, headers);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read source file: " + file.getFileName(), exception);
        }
    }

    private Map<String, String> ledgers(List<VoucherSourceRow> rows) {
        Map<String, String> ledgers = new HashMap<>();
        for (VoucherSourceRow row : rows) {
            String code = text(row.value("AC_CD"));
            if (code != null) {
                ledgers.putIfAbsent(code, text(row.value("AC_DESC")));
            }
        }
        return ledgers;
    }

    private Map<String, PartyLookup> parties(List<VoucherSourceRow> rows) {
        Map<String, PartyLookup> parties = new HashMap<>();
        for (VoucherSourceRow row : rows) {
            String ledgerCode = text(row.value("AC_CD"));
            String partyCode = text(row.value("PARTY_CODE"));
            if (ledgerCode != null && partyCode != null) {
                parties.putIfAbsent(ledgerCode + "/" + partyCode, new PartyLookup(text(row.value("PARTY_NAME")), address(row)));
            }
        }
        return parties;
    }

    private String ledgerName(Map<String, String> ledgers, String ledgerCode, VoucherMigrationSummary summary, Long migrationRunId, int excelRow, String referenceKey) {
        if (ledgerCode == null) {
            return null;
        }
        String ledgerName = ledgers.get(ledgerCode);
        if (ledgerName == null) {
            warning(summary, migrationRunId, excelRow, referenceKey, "Missing ledger for ledger_code=" + ledgerCode);
            summary.incrementMissingLedgers();
        }
        return ledgerName;
    }

    private PartyLookup party(Map<String, PartyLookup> parties, String ledgerCode, String partyCode) {
        if (ledgerCode == null || partyCode == null) {
            return null;
        }
        return parties.get(ledgerCode + "/" + partyCode);
    }

    private void validateSchemaForSummary(VoucherMigrationSummary summary) {
        List<String> issues = validateRuntimeSchema();
        if (!issues.isEmpty()) {
            summary.setSuccess(false);
            for (String issue : issues) {
                summary.addIssue(null, null, FAILED, issue);
            }
        }
    }

    private List<String> validateRuntimeSchema() {
        Set<String> headerColumns = repository.tableColumns("voucher_headers");
        Set<String> detailColumns = repository.tableColumns("voucher_details");
        List<String> issues = new java.util.ArrayList<>();
        requireColumns(issues, "voucher_headers", headerColumns, Set.of(
            "voucher_header_id", "voucher_type", "voucher_number", "voucher_date", "system_date",
            "transaction_type", "voucher_amount", "receipt_number", "temporary_receipt_number",
            "temporary_receipt_date", "contract_number", "contract_type", "contract_id", "bank_code",
            "header_control_code", "header_control_name", "remarks", "created_at", "updated_at"
        ));
        requireColumns(issues, "voucher_details", detailColumns, Set.of(
            "voucher_detail_id", "voucher_header_id", "serial_number", "category", "ledger_code",
            "ledger_name", "sub_ledger_code", "debit_amount", "credit_amount", "party_code",
            "party_name", "loan_reference", "narration", "address", "created_at", "updated_at"
        ));
        return issues;
    }

    private void requireColumns(List<String> issues, String table, Set<String> actual, Set<String> expected) {
        for (String column : expected) {
            if (!actual.contains(column)) {
                issues.add("Schema mismatch: missing column " + table + "." + column);
            }
        }
    }

    private String requiredText(VoucherSourceRow row, String headerName) {
        String value = text(row.value(headerName));
        if (value == null) {
            throw new IllegalArgumentException(headerName + " is required");
        }
        return value;
    }

    private Integer integerOrWarn(Object value, String header, VoucherMigrationSummary summary, Long migrationRunId, int excelRow, String referenceKey) {
        BigDecimal decimal = decimalOrWarn(value, header, summary, migrationRunId, excelRow, referenceKey);
        if (decimal == null) {
            return null;
        }
        try {
            return decimal.intValueExact();
        } catch (ArithmeticException exception) {
            warning(summary, migrationRunId, excelRow, referenceKey, "Invalid whole number for " + header + ": " + decimal + "; stored as NULL");
            return null;
        }
    }

    private BigDecimal decimalOrWarn(Object value, String header, VoucherMigrationSummary summary, Long migrationRunId, int excelRow, String referenceKey) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            warning(summary, migrationRunId, excelRow, referenceKey, "Invalid numeric value for " + header + ": " + text + "; stored as NULL");
            return null;
        }
    }

    private LocalDate date(Object value) {
        return value instanceof LocalDate localDate ? localDate : null;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        String normalized = value.toString().trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String narration(VoucherSourceRow row) {
        return join(text(row.value("NARR_1")), text(row.value("PTY_PH")));
    }

    private String address(VoucherSourceRow row) {
        return join(text(row.value("PTY_ADDR1")), text(row.value("PTY_ADDR2")), text(row.value("PTY_ADDR3")), text(row.value("PTY_CITY")), text(row.value("PTY_PIN")));
    }

    private String join(String... parts) {
        return java.util.Arrays.stream(parts)
            .filter(part -> part != null && !part.isBlank())
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private String voucherTypeDescription(String voucherType) {
        if (voucherType == null) {
            return null;
        }
        return switch (voucherType.toUpperCase(Locale.ROOT)) {
            case "BP" -> "Bank payment";
            case "CP" -> "Cash Payment";
            case "BR" -> "Bank Receipt";
            case "CR" -> "Cash Receipt";
            case "HJ" -> "Opening JV";
            default -> null;
        };
    }

    private void warning(VoucherMigrationSummary summary, Long migrationRunId, Integer excelRow, String referenceKey, String reason) {
        summary.incrementWarnings();
        audit(summary, migrationRunId, excelRow, referenceKey, WARNING, reason);
    }

    private void audit(VoucherMigrationSummary summary, Long migrationRunId, Integer excelRow, String referenceKey, String type, String reason) {
        summary.addIssue(excelRow, referenceKey, type, reason);
        if (migrationRunId != null) {
            migrationRunService.recordDetail(migrationRunId, excelRow, referenceKey, type, reason, null);
        }
    }

    private String finalRunStatus(VoucherMigrationSummary summary) {
        return summary.isSuccess()
            ? MigrationAuditConstants.RunStatuses.COMPLETED
            : MigrationAuditConstants.RunStatuses.FAILED;
    }

    private String referenceKey(String voucherType, String voucherNumber) {
        return safe(voucherType) + "/" + safe(voucherNumber);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Voucher row failed" : current.getMessage();
    }

    enum RowOutcome {
        INSERTED,
        DUPLICATE
    }

    record VoucherContext(
        List<VoucherSourceRow> headers,
        List<VoucherSourceRow> details,
        Map<String, List<VoucherSourceRow>> detailsByVoucher,
        Map<String, String> ledgers,
        Map<String, PartyLookup> parties
    ) {
    }

    record PartyLookup(String partyName, String address) {
    }
}
