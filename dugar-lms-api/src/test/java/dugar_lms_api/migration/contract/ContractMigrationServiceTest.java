package dugar_lms_api.migration.contract;

import dugar_lms_api.migration.common.MigrationRunService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractMigrationServiceTest {

    private static final String WARNING = "WARNING";

    @TempDir
    private Path tempDir;

    private ContractMigrationRepository repository;
    private MigrationRunService migrationRunService;
    private ContractMigrationService service;
    private String originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        repository = mock(ContractMigrationRepository.class);
        migrationRunService = mock(MigrationRunService.class);
        service = new ContractMigrationService(
            mock(ContractExcelReader.class),
            repository,
            migrationRunService,
            transactionManager()
        );
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    void contractSourceFileFoundUsesMigrationDataFileUnderUserDir() throws IOException {
        System.setProperty("user.dir", tempDir.toString());
        Path expectedFile = createMigrationDataFile();

        Path sourceFile = service.resolveContractSourceFile();

        assertThat(sourceFile).isEqualTo(expectedFile);
    }

    @Test
    void contractSourceFileMissingThrowsClearMigrationError() {
        System.setProperty("user.dir", tempDir.toString());

        assertThatThrownBy(() -> service.resolveContractSourceFile())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Contract migration source file not found: migration_data/Contracts-Active.xlsx");
    }

    @Test
    void activeWorksheetFoundIsReadFromSourceWorkbook() throws IOException {
        System.setProperty("user.dir", tempDir.toString());
        Path sourceFile = createWorkbook("Active");

        ContractExcelReader realReader = new ContractExcelReader();
        ContractMigrationService localFileService = new ContractMigrationService(
            realReader,
            repository,
            migrationRunService,
            transactionManager()
        );

        ContractExcelReader.ParsedSheet parsedSheet = localFileService.readSheet(sourceFile);

        assertThat(parsedSheet.sheetName()).isEqualTo("Active");
        assertThat(parsedSheet.headers()).contains("CONT_TYPE", "CONT_NO");
    }

    @Test
    void activeWorksheetMissingThrowsClearMigrationError() throws IOException {
        System.setProperty("user.dir", tempDir.toString());
        Path sourceFile = createWorkbook("Inactive");

        ContractExcelReader realReader = new ContractExcelReader();
        ContractMigrationService localFileService = new ContractMigrationService(
            realReader,
            repository,
            migrationRunService,
            transactionManager()
        );

        assertThatThrownBy(() -> localFileService.readSheet(sourceFile))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Worksheet 'Active' not found in Contracts-Active.xlsx");
    }

    @Test
    void normalInterestCalculationUsesLoanAmountFlatRateAndTenure() {
        BigDecimal interest = service.calculateInterestValue(new BigDecimal("100000"), new BigDecimal("12"), 24);

        assertThat(interest).isEqualByComparingTo("24000.00");
        assertThat(interest.scale()).isEqualTo(2);
    }

    @Test
    void normalBpfcCalculationUsesLoanAmountRateAndDays() {
        BigDecimal bpfcAmount = service.calculateBpfcAmount(new BigDecimal("100000"), new BigDecimal("18.25"), 30);

        assertThat(bpfcAmount).isEqualByComparingTo("1500.00");
        assertThat(bpfcAmount.scale()).isEqualTo(2);
    }

    @Test
    void decimalRoundingUsesHalfUpScale2() {
        BigDecimal interest = service.calculateInterestValue(new BigDecimal("1000"), new BigDecimal("10"), 1);
        BigDecimal bpfcAmount = service.calculateBpfcAmount(new BigDecimal("1000"), new BigDecimal("10"), 1);

        assertThat(interest).isEqualByComparingTo("8.33");
        assertThat(interest.scale()).isEqualTo(2);
        assertThat(bpfcAmount).isEqualByComparingTo("0.27");
        assertThat(bpfcAmount.scale()).isEqualTo(2);
    }

    @Test
    void zeroRateIsCalculatedAsZeroNotNull() {
        BigDecimal interest = service.calculateInterestValue(new BigDecimal("100000"), BigDecimal.ZERO, 12);
        BigDecimal bpfcAmount = service.calculateBpfcAmount(new BigDecimal("100000"), BigDecimal.ZERO, 10);

        assertThat(interest).isEqualByComparingTo("0.00");
        assertThat(bpfcAmount).isEqualByComparingTo("0.00");
    }

    @Test
    void nullInputsLeaveCalculatedValuesNullAndLogWarnings() {
        when(repository.contractExists("HP", "100")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(10L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "100",
            "STATUS", "A",
            "FIN_RATE", new BigDecimal("12"),
            "BPFC_DAYS", new BigDecimal("30")
        )), "HP", "100", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractInsert> captor = ArgumentCaptor.forClass(ContractMigrationRepository.ContractInsert.class);
        verify(repository).insertContract(captor.capture());
        assertThat(captor.getValue().financeCharges()).isNull();
        assertThat(captor.getValue().bpfcAmount()).isNull();
        assertThat(result.getInterestCalculationWarnings()).isEqualTo(1);
        assertThat(result.getBpfcCalculationWarnings()).isEqualTo(1);
        verify(migrationRunService).recordDetail(eq(99L), eq(2), eq("HP/100"), eq(WARNING), eq("Interest Value calculation skipped for contract 100: Loan Amount, Flat Rate and Tenure are required inputs"), isNull());
        verify(migrationRunService).recordDetail(eq(99L), eq(2), eq("HP/100"), eq(WARNING), eq("BPFC Amount calculation skipped for contract 100: Loan Amount, BPFC Rate and BPFC Days are required inputs"), isNull());
    }

    @Test
    void oracleComparisonWarningIsLoggedButCalculatedValuesAreStored() {
        when(repository.contractExists("HP", "101")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(11L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "101",
            "STATUS", "A",
            "FIN_AMT", new BigDecimal("100000"),
            "PERIOD", new BigDecimal("24"),
            "FIN_RATE", new BigDecimal("12"),
            "FIN_CHGS", new BigDecimal("24002.00"),
            "BPFC_DAYS", new BigDecimal("30"),
            "BPFC_RATE", new BigDecimal("18.25"),
            "BPFC_AMT", new BigDecimal("1498.00")
        )), "HP", "101", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractInsert> captor = ArgumentCaptor.forClass(ContractMigrationRepository.ContractInsert.class);
        verify(repository).insertContract(captor.capture());
        assertThat(captor.getValue().financeCharges()).isEqualByComparingTo("24000.00");
        assertThat(captor.getValue().bpfcAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.getCalculatedInterestValues()).isEqualTo(1);
        assertThat(result.getInterestCalculationWarnings()).isEqualTo(1);
        assertThat(result.getCalculatedBpfcAmounts()).isEqualTo(1);
        assertThat(result.getBpfcCalculationWarnings()).isEqualTo(1);
        verify(migrationRunService).recordDetail(eq(99L), eq(2), eq("HP/101"), eq(WARNING), eq("Interest Value Oracle comparison warning for contract 101: Oracle value=24002.00, calculated value=24000.00"), isNull());
        verify(migrationRunService).recordDetail(eq(99L), eq(2), eq("HP/101"), eq(WARNING), eq("BPFC Amount Oracle comparison warning for contract 101: Oracle value=1498.00, calculated value=1500.00"), isNull());
    }

    @Test
    void noWarningWhenOracleDifferenceIsWithinOne() {
        when(repository.contractExists("HP", "102")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(12L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "102",
            "STATUS", "A",
            "FIN_AMT", new BigDecimal("100000"),
            "PERIOD", new BigDecimal("24"),
            "FIN_RATE", new BigDecimal("12"),
            "FIN_CHGS", new BigDecimal("24001.00"),
            "BPFC_DAYS", new BigDecimal("30"),
            "BPFC_RATE", new BigDecimal("18.25"),
            "BPFC_AMT", new BigDecimal("1499.00")
        )), "HP", "102", 99L, result);

        assertThat(result.getInterestCalculationWarnings()).isZero();
        assertThat(result.getBpfcCalculationWarnings()).isZero();
        verify(migrationRunService, never()).recordDetail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void oracleImportCreatesNullableFutureContractFinancialTermsColumns() {
        when(repository.contractExists("HP", "103")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(13L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "103",
            "STATUS", "A",
            "FIN_AMT", new BigDecimal("50000"),
            "PERIOD", new BigDecimal("12"),
            "FIN_RATE", new BigDecimal("10"),
            "BPFC_DAYS", new BigDecimal("0"),
            "BPFC_RATE", BigDecimal.ZERO
        )), "HP", "103", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractInsert> captor =
            ArgumentCaptor.forClass(ContractMigrationRepository.ContractInsert.class);
        verify(repository).insertContract(captor.capture());

        ContractMigrationRepository.ContractInsert contract = captor.getValue();
        assertThat(contract.repaymentTerms()).isNull();
        assertThat(contract.moratoriumMonths()).isNull();
        assertThat(contract.paymentFrequency()).isNull();
        assertThat(contract.repaymentType()).isNull();
        assertThat(contract.enachApplicable()).isNull();
    }

    @Test
    void oracleImportCreatesNullableFutureContractDetailsFinancialTermsColumns() {
        when(repository.contractExists("HP", "105")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(15L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "105",
            "STATUS", "A",
            "FIN_AMT", new BigDecimal("50000"),
            "PERIOD", new BigDecimal("12"),
            "FIN_RATE", new BigDecimal("10"),
            "BPFC_DAYS", new BigDecimal("0"),
            "BPFC_RATE", BigDecimal.ZERO,
            "SERVICE_CHGS", new BigDecimal("1000"),
            "FIRST_EMI", new BigDecimal("2500")
        )), "HP", "105", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractDetailsInsert> captor =
            ArgumentCaptor.forClass(ContractMigrationRepository.ContractDetailsInsert.class);
        verify(repository).insertContractDetails(captor.capture());

        ContractMigrationRepository.ContractDetailsInsert details = captor.getValue();
        assertThat(details.processingCharges()).isEqualByComparingTo("1000");
        assertThat(details.emiAdvance()).isEqualByComparingTo("2500");
        assertThat(details.stampDuty()).isNull();
        assertThat(details.rtoCharges()).isNull();
        assertThat(details.valuationCharges()).isNull();
        assertThat(details.rcHoldingAmount()).isNull();
        assertThat(details.otherCharges()).isNull();
        assertThat(details.paymentDoneTo()).isNull();
        assertThat(details.payee1()).isNull();
        assertThat(details.payee2()).isNull();
        assertThat(details.payee3()).isNull();
    }

    @Test
    void documentationAreaCodeMappedToContractsFromContrDataTable() {
        when(repository.contractExists("HP", "108")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(18L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "108",
            "STATUS", "A",
            "FIN_AMT", new BigDecimal("50000"),
            "PERIOD", new BigDecimal("12"),
            "FIN_RATE", new BigDecimal("10"),
            "BPFC_DAYS", new BigDecimal("0"),
            "BPFC_RATE", BigDecimal.ZERO,
            "FLD_CODE", "AREA-7"
        )), "HP", "108", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractInsert> captor =
            ArgumentCaptor.forClass(ContractMigrationRepository.ContractInsert.class);
        verify(repository).insertContract(captor.capture());

        assertThat(captor.getValue().areaCode()).isEqualTo("AREA-7");
    }

    @Test
    void documentationDetailsMappedToContractDetailsFromContrDetailsTable() {
        when(repository.contractExists("HP", "109")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(19L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        Map<String, Object> values = new HashMap<>();
        values.put("CONT_TYPE", "HP");
        values.put("CONT_NO", "109");
        values.put("STATUS", "A");
        values.put("FIN_AMT", new BigDecimal("50000"));
        values.put("PERIOD", new BigDecimal("12"));
        values.put("FIN_RATE", new BigDecimal("10"));
        values.put("BPFC_DAYS", new BigDecimal("0"));
        values.put("BPFC_RATE", BigDecimal.ZERO);
        values.put("DOCUMENT_OBTN_BY", "DOCUSER");
        values.put("PARTY_INSP_BY", "FIUSER");
        values.put("VEHICLE_INSP_BY", "VEHUSER");
        values.put("INTRODUCED_BY", "REFUSER");
        values.put("SANCTIONED_BY", "APPROVER");

        service.importRow(row(values), "HP", "109", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractDetailsInsert> captor =
            ArgumentCaptor.forClass(ContractMigrationRepository.ContractDetailsInsert.class);
        verify(repository).insertContractDetails(captor.capture());

        ContractMigrationRepository.ContractDetailsInsert details = captor.getValue();
        assertThat(details.documentsObtainedBy()).isEqualTo("DOCUSER");
        assertThat(details.borrowerFiBy()).isEqualTo("FIUSER");
        assertThat(details.guarantorFiBy()).isEqualTo("FIUSER");
        assertThat(details.vehicleInspectionBy()).isEqualTo("VEHUSER");
        assertThat(details.loanReferredBy()).isEqualTo("REFUSER");
        assertThat(details.loanApprovedBy()).isEqualTo("APPROVER");
    }

    @Test
    void documentationBlankWorkbookMappingsRemainNullable() {
        when(repository.contractExists("HP", "110")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(20L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "110",
            "STATUS", "A",
            "FIN_AMT", new BigDecimal("50000"),
            "PERIOD", new BigDecimal("12"),
            "FIN_RATE", new BigDecimal("10"),
            "BPFC_DAYS", new BigDecimal("0"),
            "BPFC_RATE", BigDecimal.ZERO
        )), "HP", "110", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractDetailsInsert> captor =
            ArgumentCaptor.forClass(ContractMigrationRepository.ContractDetailsInsert.class);
        verify(repository).insertContractDetails(captor.capture());

        ContractMigrationRepository.ContractDetailsInsert details = captor.getValue();
        assertThat(details.documentType()).isNull();
        assertThat(details.documentVerifiedBy()).isNull();
        assertThat(details.tvrDoneBy()).isNull();
        assertThat(details.vehicleByAgency()).isNull();
        assertThat(details.propertyValuationBy()).isNull();
        assertThat(details.legalOpinionBy()).isNull();
        assertThat(details.branchCollectionToolBy()).isNull();
        assertThat(details.geoCoordinate1()).isNull();
        assertThat(details.geoCoordinate2()).isNull();
        assertThat(details.documentsCheckedBy()).isNull();
        assertThat(details.documentsVerifiedBy()).isNull();
        assertThat(details.disbursedBy()).isNull();
        assertThat(details.rcOnlineChecking()).isNull();
        assertThat(details.hoCollectionToolBy()).isNull();
        assertThat(details.hoTvrDoneBy()).isNull();
        assertThat(details.stockMarkedToBank()).isNull();
    }

    @Test
    void missingDocumentationExcelValueIsLoggedAndStoredAsNull() {
        when(repository.contractExists("HP", "111")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(21L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);
        Map<String, Object> values = new HashMap<>();
        values.put("CONT_TYPE", "HP");
        values.put("CONT_NO", "111");
        values.put("STATUS", "A");
        values.put("FIN_AMT", new BigDecimal("50000"));
        values.put("PERIOD", new BigDecimal("12"));
        values.put("FIN_RATE", new BigDecimal("10"));
        values.put("BPFC_DAYS", new BigDecimal("0"));
        values.put("BPFC_RATE", BigDecimal.ZERO);
        values.put("DOCUMENT_OBTN_BY", null);

        service.importRow(row(values), "HP", "111", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractDetailsInsert> captor =
            ArgumentCaptor.forClass(ContractMigrationRepository.ContractDetailsInsert.class);
        verify(repository).insertContractDetails(captor.capture());

        assertThat(captor.getValue().documentsObtainedBy()).isNull();
        verify(migrationRunService).recordDetail(
            eq(99L),
            eq(2),
            eq("HP/111"),
            eq(WARNING),
            eq("Missing Excel value for Obtain By (DOCUMENT_OBTN_BY); stored as NULL"),
            isNull()
        );
    }

    @Test
    void financialTermsDateMappingUsesEmiStartDateFromColumnCAndAgreementDateFromColumnD() {
        when(repository.contractExists("HP", "104")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(14L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "104",
            "STATUS", "A",
            "CONT_DT", LocalDate.of(2026, 1, 5),
            "EFF_DT", LocalDate.of(2026, 1, 1),
            "FIN_AMT", new BigDecimal("50000"),
            "PERIOD", new BigDecimal("12"),
            "FIN_RATE", new BigDecimal("10"),
            "BPFC_DAYS", new BigDecimal("0"),
            "BPFC_RATE", BigDecimal.ZERO
        )), "HP", "104", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractInsert> captor =
            ArgumentCaptor.forClass(ContractMigrationRepository.ContractInsert.class);
        verify(repository).insertContract(captor.capture());

        assertThat(captor.getValue().firstEmiDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(captor.getValue().contractDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void invalidNumericValueIsLoggedAsWarningAndStoredAsNull() {
        when(repository.contractExists("HP", "106")).thenReturn(false);
        when(repository.insertContract(any())).thenReturn(16L);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "106",
            "STATUS", "A",
            "FIN_AMT", "not-a-number",
            "PERIOD", new BigDecimal("12"),
            "FIN_RATE", new BigDecimal("10"),
            "BPFC_DAYS", new BigDecimal("0"),
            "BPFC_RATE", BigDecimal.ZERO
        )), "HP", "106", 99L, result);

        ArgumentCaptor<ContractMigrationRepository.ContractInsert> captor =
            ArgumentCaptor.forClass(ContractMigrationRepository.ContractInsert.class);
        verify(repository).insertContract(captor.capture());

        assertThat(captor.getValue().loanAmount()).isNull();
        verify(migrationRunService).recordDetail(
            eq(99L),
            eq(2),
            eq("HP/106"),
            eq(WARNING),
            eq("Invalid numeric value for FIN_AMT: not-a-number; stored as NULL"),
            isNull()
        );
    }

    @Test
    void duplicateContractDoesNotInsertContractDetails() {
        when(repository.contractExists("HP", "107")).thenReturn(true);
        ContractMigrationResult result = ContractMigrationResult.forFile("contracts.xlsx", "Active", 1);

        ContractMigrationService.RowResult rowResult = service.importRow(row(Map.of(
            "CONT_TYPE", "HP",
            "CONT_NO", "107",
            "STATUS", "A"
        )), "HP", "107", 99L, result);

        assertThat(rowResult).isEqualTo(ContractMigrationService.RowResult.DUPLICATE);
        verify(repository, never()).insertContract(any());
        verify(repository, never()).insertContractDetails(any());
    }

    private ContractExcelReader.LegacyRow row(Map<String, Object> values) {
        Map<String, Object> normalized = new HashMap<>();
        values.forEach((key, value) -> normalized.put(key.toUpperCase(), value));
        return new ContractExcelReader.LegacyRow(2, normalized);
    }

    private Path createMigrationDataFile() throws IOException {
        Path migrationDataDirectory = Files.createDirectories(tempDir.resolve("migration_data"));
        Path sourceFile = migrationDataDirectory.resolve("Contracts-Active.xlsx");
        Files.write(sourceFile, new byte[] {1});
        return sourceFile;
    }

    private Path createWorkbook(String sheetName) throws IOException {
        Path migrationDataDirectory = Files.createDirectories(tempDir.resolve("migration_data"));
        Path sourceFile = migrationDataDirectory.resolve("Contracts-Active.xlsx");
        try (Workbook workbook = new XSSFWorkbook(); OutputStream outputStream = Files.newOutputStream(sourceFile)) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("CONT_TYPE");
            header.createCell(1).setCellValue("CONT_NO");
            workbook.write(outputStream);
        }
        return sourceFile;
    }

    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
