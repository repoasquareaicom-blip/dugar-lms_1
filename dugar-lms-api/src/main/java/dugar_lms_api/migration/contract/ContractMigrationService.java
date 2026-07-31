package dugar_lms_api.migration.contract;

import dugar_lms_api.migration.common.MigrationAuditConstants;
import dugar_lms_api.migration.common.MigrationRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ContractMigrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContractMigrationService.class);

    private static final String SOURCE_FILE_NAME = "Contracts-Active.xlsx";
    private static final String SOURCE_FILE_RELATIVE_PATH = "migration_data/Contracts-Active.xlsx";
    private static final String SHEET_NAME = "Active";
    private static final String CREATED_BY = "LEGACY_MIGRATION";
    private static final String DUPLICATE_REASON = "Duplicate contract already exists";
    private static final String SCHEMA_FAILURE_REASON = "Schema validation failed";
    private static final String WARNING_RESULT_TYPE = "WARNING";
    private static final BigDecimal INTEREST_DIVISOR = BigDecimal.valueOf(1200);
    private static final BigDecimal BPFC_DIVISOR = BigDecimal.valueOf(36500);
    private static final BigDecimal ORACLE_COMPARISON_TOLERANCE = BigDecimal.valueOf(1).setScale(2, RoundingMode.HALF_UP);

    private final ContractExcelReader contractExcelReader;
    private final ContractMigrationRepository contractMigrationRepository;
    private final MigrationRunService migrationRunService;
    private final TransactionTemplate rowTransactionTemplate;

    public ContractMigrationService(
        ContractExcelReader contractExcelReader,
        ContractMigrationRepository contractMigrationRepository,
        MigrationRunService migrationRunService,
        PlatformTransactionManager transactionManager
    ) {
        this.contractExcelReader = contractExcelReader;
        this.contractMigrationRepository = contractMigrationRepository;
        this.migrationRunService = migrationRunService;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rowTransactionTemplate = transactionTemplate;
    }

    public ContractMigrationResult prepareContractMigration() {
        Path sourceFile = resolveContractSourceFile();
        ContractExcelReader.ParsedSheet parsedSheet = readSheet(sourceFile);
        ContractMigrationResult result = ContractMigrationResult.forFile(SOURCE_FILE_NAME, SHEET_NAME, parsedSheet.rows().size());

        List<String> schemaIssues = validateRuntimeSchema();
        if (!schemaIssues.isEmpty()) {
            result.setSuccess(false);
            result.setFailed(result.getTotalRows());
            for (String issue : schemaIssues) {
                result.addError(0, null, null, issue);
            }
        }

        return result;
    }

    public ContractMigrationResult importContracts() {
        Path sourceFile = resolveContractSourceFile();
        ContractExcelReader.ParsedSheet parsedSheet = readSheet(sourceFile);
        ContractMigrationResult result = ContractMigrationResult.forFile(SOURCE_FILE_NAME, SHEET_NAME, parsedSheet.rows().size());
        Long migrationRunId = migrationRunService.startRun(
            MigrationAuditConstants.MigrationTypes.CONTRACT,
            SOURCE_FILE_NAME,
            SHEET_NAME,
            parsedSheet.rows().size(),
            CREATED_BY
        );
        result.setMigrationRunId(migrationRunId);

        List<String> schemaIssues = validateRuntimeSchema();
        if (!schemaIssues.isEmpty()) {
            result.setSuccess(false);
            result.setFailed(result.getTotalRows());
            for (String issue : schemaIssues) {
                result.addError(0, null, null, issue);
                migrationRunService.recordFailure(migrationRunId, null, null, issue, null);
            }
            migrationRunService.failRun(
                migrationRunId,
                result.getInserted(),
                result.getDuplicatesSkipped(),
                result.getFailed(),
                SCHEMA_FAILURE_REASON
            );
            return result;
        }

        for (ContractExcelReader.LegacyRow row : parsedSheet.rows()) {
            String contractType = toTrimmedString(row.value("CONT_TYPE"));
            String contractNumber = toTrimmedString(row.value("CONT_NO"));

            try {
                RowResult rowResult = rowTransactionTemplate.execute(status -> importRow(row, contractType, contractNumber, migrationRunId, result));
                if (rowResult == RowResult.INSERTED) {
                    result.incrementInserted();
                } else {
                    result.incrementDuplicatesSkipped();
                    result.addDuplicate(row.excelRow(), contractType, contractNumber, DUPLICATE_REASON);
                    migrationRunService.recordDuplicate(
                        migrationRunId,
                        row.excelRow(),
                        referenceKey(contractType, contractNumber),
                        DUPLICATE_REASON,
                        null
                    );
                }
            } catch (Exception exception) {
                String reason = rootMessage(exception);
                result.incrementFailed();
                result.addError(row.excelRow(), contractType, contractNumber, reason);
                migrationRunService.recordFailure(
                    migrationRunId,
                    row.excelRow(),
                    referenceKey(contractType, contractNumber),
                    reason,
                    null
                );
            }
        }

        result.setSuccess(result.getFailed() == 0);
        migrationRunService.completeRun(
            migrationRunId,
            result.getInserted(),
            result.getDuplicatesSkipped(),
            result.getFailed(),
            finalRunStatus(result)
        );
        return result;
    }

    Path resolveContractSourceFile() {
        Path sourceFile = Paths.get(
            System.getProperty("user.dir"),
            "migration_data",
            SOURCE_FILE_NAME
        );
        if (!Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException("Contract migration source file not found: " + SOURCE_FILE_RELATIVE_PATH);
        }
        return sourceFile;
    }

    ContractExcelReader.ParsedSheet readSheet(Path sourceFile) {
        try (InputStream inputStream = Files.newInputStream(sourceFile)) {
            return contractExcelReader.read(inputStream, SHEET_NAME, SOURCE_FILE_NAME);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read contract migration source file", exception);
        }
    }

    private List<String> validateRuntimeSchema() {
        List<String> issues = new ArrayList<>();

        Set<String> contractsColumns = contractMigrationRepository.tableColumns("contracts");
        Set<String> contractDetailsColumns = contractMigrationRepository.tableColumns("contract_details");

        requireColumns(issues, "contracts", contractsColumns, Set.of(
            "contract_id",
            "contract_type",
            "contract_number",
            "legacy_contract_number",
            "contract_date",
            "loan_amount",
            "first_emi_date",
            "tenure_months",
            "flat_interest_rate",
            "insurance_deposit",
            "finance_charges",
            "bpfc_days",
            "bpfc_rate",
            "bpfc_amount",
            "total_contract_value",
            "prompt_payment_rebate",
            "irr_rate",
            "borrower_code",
            "guarantor_code",
            "area_code",
            "registration_number",
            "vehicle_make",
            "equipment_model",
            "loan_close_date",
            "category",
            "pdc_last_date",
            "mode_of_payment",
            "repayment_terms",
            "moratorium_months",
            "payment_frequency",
            "repayment_type",
            "enach_applicable",
            "vehicle_age",
            "status",
            "created_by",
            "is_active"
        ));

        requireColumns(issues, "contract_details", contractDetailsColumns, Set.of(
            "contract_detail_id",
            "contract_id",
            "existing_loan_details",
            "linked_account",
            "processing_charges",
            "emi_advance",
            "state_code",
            "additional_collateral",
            "engine_number",
            "chassis_number",
            "registration_date",
            "document_type",
            "document_verified_by",
            "loan_approved_by",
            "borrower_fi_by",
            "guarantor_fi_by",
            "tvr_done_by",
            "vehicle_by_agency",
            "vehicle_inspection_by",
            "property_valuation_by",
            "legal_opinion_by",
            "branch_collection_tool_by",
            "geo_coordinate_1",
            "geo_coordinate_2",
            "documents_obtained_by",
            "documents_checked_by",
            "documents_verified_by",
            "loan_referred_by",
            "disbursed_by",
            "rc_online_checking",
            "ho_collection_tool_by",
            "ho_tvr_done_by",
            "stock_marked_to_bank",
            "owner_serial_number",
            "stamp_duty",
            "rto_charges",
            "valuation_charges",
            "rc_holding_amount",
            "other_charges",
            "payment_done_to",
            "payee_1",
            "payee_2",
            "payee_3",
            "created_by",
            "is_active"
        ));

        return issues;
    }

    private void requireColumns(List<String> issues, String tableName, Set<String> actualColumns, Set<String> expectedColumns) {
        for (String expected : expectedColumns) {
            if (!actualColumns.contains(expected)) {
                issues.add("Schema mismatch: missing column " + tableName + "." + expected);
            }
        }
    }

    RowResult importRow(
        ContractExcelReader.LegacyRow row,
        String contractType,
        String contractNumber,
        Long migrationRunId,
        ContractMigrationResult result
    ) {
        if (isBlank(contractType) || isBlank(contractNumber)) {
            throw new IllegalArgumentException("CONT_TYPE and CONT_NO are required");
        }

        if (contractMigrationRepository.contractExists(contractType, contractNumber)) {
            return RowResult.DUPLICATE;
        }

        BigDecimal loanAmount = toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "FIN_AMT");
        Integer tenureMonths = toIntegerOrWarn(row, migrationRunId, contractType, contractNumber, "PERIOD");
        BigDecimal flatInterestRate = toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "FIN_RATE");
        BigDecimal oracleInterestValue = toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "FIN_CHGS");
        BigDecimal bpfcRate = toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "BPFC_RATE");
        Integer bpfcDays = toIntegerOrWarn(row, migrationRunId, contractType, contractNumber, "BPFC_DAYS");
        BigDecimal oracleBpfcAmount = toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "BPFC_AMT");
        String areaCode = toTrimmedStringOrWarn(row, migrationRunId, contractType, contractNumber, "FLD_CODE", "Area Code");
        String documentObtainedBy = toTrimmedStringOrWarn(row, migrationRunId, contractType, contractNumber, "DOCUMENT_OBTN_BY", "Obtain By");
        String partyInspectionBy = toTrimmedStringOrWarn(row, migrationRunId, contractType, contractNumber, "PARTY_INSP_BY", "FI Borrower/FI Guarantors");
        String vehicleInspectionBy = toTrimmedStringOrWarn(row, migrationRunId, contractType, contractNumber, "VEHICLE_INSP_BY", "Vehicle Inspection By");
        String loanReferredBy = toTrimmedStringOrWarn(row, migrationRunId, contractType, contractNumber, "INTRODUCED_BY", "Loan Referred By");
        String loanApprovedBy = toTrimmedStringOrWarn(row, migrationRunId, contractType, contractNumber, "SANCTIONED_BY", "Loan Approved By");

        BigDecimal calculatedInterestValue = calculateInterestValue(loanAmount, flatInterestRate, tenureMonths);
        if (calculatedInterestValue == null) {
            result.incrementInterestCalculationWarnings();
            recordWarning(
                migrationRunId,
                row.excelRow(),
                contractType,
                contractNumber,
                "Interest Value calculation skipped for contract " + contractNumber
                    + ": Loan Amount, Flat Rate and Tenure are required inputs"
            );
        } else {
            result.incrementCalculatedInterestValues();
            recordComparisonWarningIfNeeded(
                migrationRunId,
                row.excelRow(),
                contractType,
                contractNumber,
                "Interest Value",
                oracleInterestValue,
                calculatedInterestValue,
                result::incrementInterestCalculationWarnings
            );
        }

        BigDecimal calculatedBpfcAmount = calculateBpfcAmount(loanAmount, bpfcRate, bpfcDays);
        if (calculatedBpfcAmount == null) {
            result.incrementBpfcCalculationWarnings();
            recordWarning(
                migrationRunId,
                row.excelRow(),
                contractType,
                contractNumber,
                "BPFC Amount calculation skipped for contract " + contractNumber
                    + ": Loan Amount, BPFC Rate and BPFC Days are required inputs"
            );
        } else {
            result.incrementCalculatedBpfcAmounts();
            recordComparisonWarningIfNeeded(
                migrationRunId,
                row.excelRow(),
                contractType,
                contractNumber,
                "BPFC Amount",
                oracleBpfcAmount,
                calculatedBpfcAmount,
                result::incrementBpfcCalculationWarnings
            );
        }

        ContractMigrationRepository.ContractInsert contract = new ContractMigrationRepository.ContractInsert(
            contractType,
            contractNumber,
            contractNumber,
            toLocalDate(row.value("EFF_DT")),
            loanAmount,
            toLocalDate(row.value("CONT_DT")),
            tenureMonths,
            flatInterestRate,
            toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "INS_DEPOSIT"),
            calculatedInterestValue,
            bpfcDays,
            bpfcRate,
            calculatedBpfcAmount,
            toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "TOT_CONT"),
            toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "PPR_PER"),
            toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "IRR_RATE"),
            toTrimmedString(row.value("HIRER_CODE")),
            toTrimmedString(row.value("GUAR_CODE")),
            areaCode,
            toTrimmedString(row.value("REGIS_NO")),
            toTrimmedString(row.value("EQUP_CODE")),
            toTrimmedString(row.value("EQUP_MODEL")),
            toLocalDate(row.value("CLOSE_DATE")),
            toTrimmedString(row.value("RISKGRADE")),
            toLocalDate(row.value("PDCDATE")),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            requiredText(toTrimmedString(row.value("STATUS")), "STATUS"),
            CREATED_BY,
            true
        );

        Long contractId = contractMigrationRepository.insertContract(contract);

        ContractMigrationRepository.ContractDetailsInsert details = new ContractMigrationRepository.ContractDetailsInsert(
            contractId,
            toTrimmedString(row.value("EXISTING_HP_NO")),
            toTrimmedString(row.value("OTHER_VEHICLES")),
            toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "SERVICE_CHGS"),
            toBigDecimalOrWarn(row, migrationRunId, contractType, contractNumber, "FIRST_EMI"),
            toTrimmedString(row.value("STATE_CODE")),
            toTrimmedString(row.value("SECURITY_OFFERED")),
            toTrimmedString(row.value("ENGINE_NO")),
            toTrimmedString(row.value("CHASIS_NO")),
            toLocalDate(row.value("REGN_DATE")),
            null,
            null,
            loanApprovedBy,
            partyInspectionBy,
            partyInspectionBy,
            null,
            null,
            vehicleInspectionBy,
            null,
            null,
            null,
            null,
            null,
            documentObtainedBy,
            null,
            null,
            loanReferredBy,
            null,
            null,
            null,
            null,
            null,
            toTrimmedString(row.value("NO_OF_OWNERSHIP")),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            CREATED_BY,
            true
        );

        contractMigrationRepository.insertContractDetails(details);

        return RowResult.INSERTED;
    }

    BigDecimal calculateInterestValue(BigDecimal loanAmount, BigDecimal flatRate, Integer tenureMonths) {
        if (loanAmount == null || flatRate == null || tenureMonths == null) {
            return null;
        }

        return loanAmount
            .multiply(flatRate)
            .multiply(BigDecimal.valueOf(tenureMonths))
            .divide(INTEREST_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    BigDecimal calculateBpfcAmount(BigDecimal loanAmount, BigDecimal bpfcRate, Integer bpfcDays) {
        if (loanAmount == null || bpfcRate == null || bpfcDays == null) {
            return null;
        }

        return loanAmount
            .multiply(bpfcRate)
            .multiply(BigDecimal.valueOf(bpfcDays))
            .divide(BPFC_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    private void recordComparisonWarningIfNeeded(
        Long migrationRunId,
        int excelRow,
        String contractType,
        String contractNumber,
        String label,
        BigDecimal oracleValue,
        BigDecimal calculatedValue,
        Runnable counter
    ) {
        if (oracleValue == null || calculatedValue == null) {
            return;
        }

        BigDecimal difference = oracleValue.subtract(calculatedValue).abs();
        if (difference.compareTo(ORACLE_COMPARISON_TOLERANCE) > 0) {
            counter.run();
            recordWarning(
                migrationRunId,
                excelRow,
                contractType,
                contractNumber,
                label + " Oracle comparison warning for contract " + contractNumber
                    + ": Oracle value=" + scale2(oracleValue)
                    + ", calculated value=" + calculatedValue
            );
        }
    }

    private void recordWarning(
        Long migrationRunId,
        int excelRow,
        String contractType,
        String contractNumber,
        String reason
    ) {
        migrationRunService.recordDetail(
            migrationRunId,
            excelRow,
            referenceKey(contractType, contractNumber),
            WARNING_RESULT_TYPE,
            reason,
            null
        );
    }

    private BigDecimal scale2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String requiredText(String value, String header) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(header + " is required");
        }
        return value;
    }

    private String toTrimmedString(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            return normalized.isEmpty() ? null : normalized;
        }

        if (value instanceof BigDecimal decimalValue) {
            return decimalValue.stripTrailingZeros().toPlainString();
        }

        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String toTrimmedStringOrWarn(
        ContractExcelReader.LegacyRow row,
        Long migrationRunId,
        String contractType,
        String contractNumber,
        String headerName,
        String fieldLabel
    ) {
        String value = toTrimmedString(row.value(headerName));
        if (value == null && row.hasHeader(headerName)) {
            recordWarning(
                migrationRunId,
                row.excelRow(),
                contractType,
                contractNumber,
                "Missing Excel value for " + fieldLabel + " (" + headerName + "); stored as NULL"
            );
        }
        return value;
    }

    private BigDecimal toBigDecimal(Object value, String headerName) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal decimalValue) {
            return decimalValue;
        }

        String normalized = toTrimmedString(value);
        if (isBlank(normalized)) {
            return null;
        }

        try {
            return new BigDecimal(normalized.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid numeric value for " + headerName + ": " + normalized);
        }
    }

    private Integer toInteger(Object value, String headerName) {
        BigDecimal decimalValue = toBigDecimal(value, headerName);
        if (decimalValue == null) {
            return null;
        }

        try {
            return decimalValue.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Expected whole number for " + headerName + ": " + decimalValue);
        }
    }

    private BigDecimal toBigDecimalOrWarn(
        ContractExcelReader.LegacyRow row,
        Long migrationRunId,
        String contractType,
        String contractNumber,
        String headerName
    ) {
        try {
            return toBigDecimal(row.value(headerName), headerName);
        } catch (IllegalArgumentException exception) {
            recordWarning(
                migrationRunId,
                row.excelRow(),
                contractType,
                contractNumber,
                exception.getMessage() + "; stored as NULL"
            );
            return null;
        }
    }

    private Integer toIntegerOrWarn(
        ContractExcelReader.LegacyRow row,
        Long migrationRunId,
        String contractType,
        String contractNumber,
        String headerName
    ) {
        try {
            return toInteger(row.value(headerName), headerName);
        } catch (IllegalArgumentException exception) {
            recordWarning(
                migrationRunId,
                row.excelRow(),
                contractType,
                contractNumber,
                exception.getMessage() + "; stored as NULL"
            );
            return null;
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String referenceKey(String contractType, String contractNumber) {
        return contractType + "/" + contractNumber;
    }

    private String finalRunStatus(ContractMigrationResult result) {
        if (result.getFailed() == 0) {
            return MigrationAuditConstants.RunStatuses.COMPLETED;
        }

        if (result.getInserted() > 0) {
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

    enum RowResult {
        INSERTED,
        DUPLICATE
    }
}
