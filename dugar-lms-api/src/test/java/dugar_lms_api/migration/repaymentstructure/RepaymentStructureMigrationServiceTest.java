package dugar_lms_api.migration.repaymentstructure;

import dugar_lms_api.migration.common.MigrationRunService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepaymentStructureMigrationServiceTest {

    private RepaymentStructureMigrationRepository repository;
    private MigrationRunService migrationRunService;
    private RepaymentStructureMigrationService service;

    @TempDir
    private Path migrationDataDirectory;

    @BeforeEach
    void setUp() {
        repository = mock(RepaymentStructureMigrationRepository.class);
        migrationRunService = mock(MigrationRunService.class);
        service = new RepaymentStructureMigrationService(
            new RepaymentStructureExcelReader(),
            repository,
            migrationRunService,
            transactionManager(),
            migrationDataDirectory
        );
    }

    @Test
    void validSingleRow() {
        givenContract(10L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.empty());

        assertThat(service.importRow(row("HP", "100", "1", "6", "18000"), new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isEqualTo(RepaymentStructureMigrationService.RowOutcome.INSERTED);
        verify(repository).insert(new RepaymentStructureRecord(10L, 1, 6, new BigDecimal("18000")));
    }

    @Test
    void validMultipleRepaymentSlabsForOneContract() {
        givenContract(10L);
        when(repository.findExisting(eq(10L), any())).thenReturn(Optional.empty());
        RepaymentStructureMigrationService.SourceKeyTracker tracker = new RepaymentStructureMigrationService.SourceKeyTracker();

        assertThat(service.importRow(row("HP", "100", "1", "6", "18000"), tracker, true)).isEqualTo(RepaymentStructureMigrationService.RowOutcome.INSERTED);
        assertThat(service.importRow(row("HP", "100", "2", "5", "14000"), tracker, true)).isEqualTo(RepaymentStructureMigrationService.RowOutcome.INSERTED);
    }

    @Test
    void validMultipleContracts() {
        when(repository.findContractIds("HP", "100")).thenReturn(List.of(10L));
        when(repository.findContractIds("LS", "200")).thenReturn(List.of(20L));
        when(repository.findExisting(any(), any())).thenReturn(Optional.empty());

        assertThat(service.importRow(row("HP", "100", "1", "6", "18000"), new RepaymentStructureMigrationService.SourceKeyTracker(), true)).isEqualTo(RepaymentStructureMigrationService.RowOutcome.INSERTED);
        assertThat(service.importRow(row("LS", "200", "1", "3", "1000"), new RepaymentStructureMigrationService.SourceKeyTracker(), true)).isEqualTo(RepaymentStructureMigrationService.RowOutcome.INSERTED);
    }

    @Test
    void missingContract() {
        when(repository.findContractIds("HP", "999")).thenReturn(List.of());

        assertThatThrownBy(() -> service.importRow(row("HP", "999", "1", "6", "18000"), new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isInstanceOf(RepaymentStructureMigrationService.MissingContractException.class);
    }

    @Test
    void ambiguousContractIsConflict() {
        when(repository.findContractIds("HP", "100")).thenReturn(List.of(10L, 11L));

        assertThatThrownBy(() -> service.importRow(row("HP", "100", "1", "6", "18000"), new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isInstanceOf(RepaymentStructureMigrationService.AmbiguousContractException.class);
    }

    @Test
    void invalidSequenceBlank() {
        assertInvalidSequence(row("HP", "100", null, "6", "18000"));
    }

    @Test
    void invalidSequenceZero() {
        assertInvalidSequence(row("HP", "100", "0", "6", "18000"));
    }

    @Test
    void invalidSequenceNegative() {
        assertInvalidSequence(row("HP", "100", "-1", "6", "18000"));
    }

    @Test
    void invalidSequenceDecimalNonWhole() {
        assertInvalidSequence(row("HP", "100", "1.5", "6", "18000"));
    }

    @Test
    void invalidNumberOfInstallmentsBlank() {
        assertInvalidInstallmentCount(row("HP", "100", "1", null, "18000"));
    }

    @Test
    void invalidNumberOfInstallmentsZero() {
        assertInvalidInstallmentCount(row("HP", "100", "1", "0", "18000"));
    }

    @Test
    void invalidNumberOfInstallmentsNegative() {
        assertInvalidInstallmentCount(row("HP", "100", "1", "-6", "18000"));
    }

    @Test
    void invalidInstallmentAmountBlank() {
        assertInvalidAmount(row("HP", "100", "1", "6", null));
    }

    @Test
    void invalidInstallmentAmountZero() {
        assertInvalidAmount(row("HP", "100", "1", "6", "0"));
    }

    @Test
    void invalidInstallmentAmountNegative() {
        assertInvalidAmount(row("HP", "100", "1", "6", "-1"));
    }

    @Test
    void existingIdenticalRowBecomesDuplicate() {
        givenContract(10L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.of(existing(10L, 1, 6, "18000.00")));

        assertThat(service.importRow(row("HP", "100", "1", "6", "18000"), new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isEqualTo(RepaymentStructureMigrationService.RowOutcome.DUPLICATE);
        verify(repository, never()).insert(any());
    }

    @Test
    void existingDifferentInstallmentCountBecomesConflict() {
        givenContract(10L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.of(existing(10L, 1, 5, "18000")));

        assertThatThrownBy(() -> service.importRow(row("HP", "100", "1", "6", "18000"), new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isInstanceOf(RepaymentStructureMigrationService.RepaymentStructureConflictException.class)
            .hasMessageContaining("existing count=5");
    }

    @Test
    void existingDifferentAmountBecomesConflict() {
        givenContract(10L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.of(existing(10L, 1, 6, "17000")));

        assertThatThrownBy(() -> service.importRow(row("HP", "100", "1", "6", "18000"), new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isInstanceOf(RepaymentStructureMigrationService.RepaymentStructureConflictException.class)
            .hasMessageContaining("amount=17000");
    }

    @Test
    void duplicateIdenticalRowsInsideExcel() {
        givenContract(10L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.empty());
        RepaymentStructureMigrationService.SourceKeyTracker tracker = new RepaymentStructureMigrationService.SourceKeyTracker();

        assertThat(service.importRow(row("HP", "100", "1", "6", "18000"), tracker, true)).isEqualTo(RepaymentStructureMigrationService.RowOutcome.INSERTED);
        assertThat(service.importRow(row("HP", "100", "1", "6", "18000.00"), tracker, true)).isEqualTo(RepaymentStructureMigrationService.RowOutcome.DUPLICATE);
    }

    @Test
    void conflictingRowsInsideExcel() {
        givenContract(10L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.empty());
        RepaymentStructureMigrationService.SourceKeyTracker tracker = new RepaymentStructureMigrationService.SourceKeyTracker();

        assertThat(service.importRow(row("HP", "100", "1", "6", "18000"), tracker, true)).isEqualTo(RepaymentStructureMigrationService.RowOutcome.INSERTED);
        assertThatThrownBy(() -> service.importRow(row("HP", "100", "1", "5", "18000"), tracker, true))
            .isInstanceOf(RepaymentStructureMigrationService.RepaymentStructureConflictException.class);
    }

    @Test
    void completelyBlankRowsIgnored() {
        assertThat(service.importRow(new RepaymentStructureSourceRow(3, null, null, null, null, null), new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isEqualTo(RepaymentStructureMigrationService.RowOutcome.SKIPPED_BLANK);
    }

    @Test
    void numericContractNumberFormattingDoesNotAddDecimalSuffix() {
        assertThat(service.normalizeContract("100.0")).isEqualTo("100");
    }

    @Test
    void missingRequiredExcelHeader() throws Exception {
        RepaymentStructureExcelReader reader = new RepaymentStructureExcelReader();

        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(workbookBytes(List.of("CONT_TYPE", "CONT_NO", "SNO", "NO_OF_INST"), List.of())), RepaymentStructureMigrationService.SOURCE_SHEET_NAME))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INST_AMT");
    }

    @Test
    void prepareDoesNotInsert() throws Exception {
        givenSchema();
        givenContract(10L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.empty());

        writeSourceWorkbook(defaultHeaders(), List.of(List.of("HP", "100", "1", "6", "18000")));
        RepaymentStructureMigrationSummary summary = service.prepareRepaymentStructureMigration();

        assertThat(summary.getEligibleRows()).isEqualTo(1);
        assertThat(summary.getInserted()).isZero();
        verify(repository, never()).insert(any());
    }

    @Test
    void importInsertsEligibleRows() throws Exception {
        givenSchema();
        givenContract(10L);
        when(migrationRunService.startRun(any(), any(), any(), anyInt(), any())).thenReturn(99L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.empty());

        writeSourceWorkbook(defaultHeaders(), List.of(List.of("HP", "100", "1", "6", "18000")));
        RepaymentStructureMigrationSummary summary = service.importRepaymentStructures();

        assertThat(summary.getInserted()).isEqualTo(1);
        verify(repository).insert(any());
    }

    @Test
    void importRerunReportsDuplicatesAndInsertsZero() throws Exception {
        givenSchema();
        givenContract(10L);
        when(migrationRunService.startRun(any(), any(), any(), anyInt(), any())).thenReturn(99L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.of(existing(10L, 1, 6, "18000")));

        writeSourceWorkbook(defaultHeaders(), List.of(List.of("HP", "100", "1", "6", "18000")));
        RepaymentStructureMigrationSummary summary = service.importRepaymentStructures();

        assertThat(summary.getInserted()).isZero();
        assertThat(summary.getDuplicates()).isEqualTo(1);
        assertThat(summary.isSuccess()).isTrue();
        assertThat(summary.isCompletedWithWarnings()).isTrue();
    }

    @Test
    void summaryCountsReconcile() throws Exception {
        givenSchema();
        givenContract(10L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.empty());

        writeSourceWorkbook(defaultHeaders(), List.of(
            List.of("HP", "100", "1", "6", "18000"),
            List.of("", "", "", "", "")
        ));
        RepaymentStructureMigrationSummary summary = service.prepareRepaymentStructureMigration();

        assertThat(summary.getTotalSourceRows()).isEqualTo(1);
        assertThat(summary.getEligibleRows()).isEqualTo(1);
        assertThat(summary.getSkippedBlankRows()).isEqualTo(1);
    }

    @Test
    void completedWithWarningsTrueForWarningRows() {
        RepaymentStructureMigrationSummary summary = RepaymentStructureMigrationSummary.base();
        summary.incrementMissingContracts();
        summary.completeProcessStatus();

        assertThat(summary.isCompletedWithWarnings()).isTrue();
    }

    @Test
    void successRemainsTrueForCompletedMigrationWithWarnings() {
        RepaymentStructureMigrationSummary summary = RepaymentStructureMigrationSummary.base();
        summary.incrementConflicts();
        summary.completeProcessStatus();

        assertThat(summary.isSuccess()).isTrue();
    }

    @Test
    void unexpectedRowErrorLoggedAsFailedWithoutTechnicalDetails() throws Exception {
        givenSchema();
        givenContract(10L);
        when(migrationRunService.startRun(any(), any(), any(), anyInt(), any())).thenReturn(99L);
        when(repository.findExisting(10L, 1)).thenReturn(Optional.empty());
        when(repository.insert(any())).thenThrow(new RuntimeException("raw database stack detail"));

        writeSourceWorkbook(defaultHeaders(), List.of(List.of("HP", "100", "1", "6", "18000")));
        RepaymentStructureMigrationSummary summary = service.importRepaymentStructures();

        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getIssues().getFirst().reason()).isEqualTo("Row processing failed");
    }

    private void assertInvalidSequence(RepaymentStructureSourceRow row) {
        assertThatThrownBy(() -> service.importRow(row, new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isInstanceOf(RepaymentStructureMigrationService.InvalidSequenceException.class);
    }

    private void assertInvalidInstallmentCount(RepaymentStructureSourceRow row) {
        assertThatThrownBy(() -> service.importRow(row, new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isInstanceOf(RepaymentStructureMigrationService.InvalidInstallmentCountException.class);
    }

    private void assertInvalidAmount(RepaymentStructureSourceRow row) {
        assertThatThrownBy(() -> service.importRow(row, new RepaymentStructureMigrationService.SourceKeyTracker(), true))
            .isInstanceOf(RepaymentStructureMigrationService.InvalidInstallmentAmountException.class);
    }

    private RepaymentStructureSourceRow row(String contractType, String contractNumber, String sequenceNo, String count, String amount) {
        return new RepaymentStructureSourceRow(2, contractType, contractNumber, sequenceNo, count, amount);
    }

    private ExistingRepaymentStructure existing(Long contractId, Integer sequenceNo, Integer count, String amount) {
        return new ExistingRepaymentStructure(1L, contractId, sequenceNo, count, new BigDecimal(amount));
    }

    private void givenContract(Long contractId) {
        when(repository.findContractIds("HP", "100")).thenReturn(List.of(contractId));
    }

    private void givenSchema() {
        when(repository.tableColumns(RepaymentStructureMigrationService.TARGET_TABLE)).thenReturn(Set.of(
            "repayment_structure_id",
            "contract_id",
            "sequence_no",
            "number_of_installments",
            "installment_amount",
            "created_at",
            "updated_at"
        ));
    }

    private List<String> defaultHeaders() {
        return List.of("CONT_TYPE", "CONT_NO", "SNO", "NO_OF_INST", "INST_AMT");
    }

    private void writeSourceWorkbook(List<String> headers, List<List<String>> rows) throws Exception {
        Files.write(migrationDataDirectory.resolve(RepaymentStructureMigrationService.SOURCE_FILE_NAME), workbookBytes(headers, rows));
    }

    private byte[] workbookBytes(List<String> headers, List<List<String>> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(RepaymentStructureMigrationService.SOURCE_SHEET_NAME);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
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
