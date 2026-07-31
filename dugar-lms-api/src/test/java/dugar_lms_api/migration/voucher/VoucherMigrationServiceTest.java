package dugar_lms_api.migration.voucher;

import dugar_lms_api.migration.common.MigrationRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherMigrationServiceTest {

    @TempDir
    private Path tempDir;

    private VoucherExcelReader excelReader;
    private VoucherMigrationRepository repository;
    private MigrationRunService migrationRunService;
    private VoucherMigrationService service;

    @BeforeEach
    void setUp() {
        excelReader = mock(VoucherExcelReader.class);
        repository = mock(VoucherMigrationRepository.class);
        migrationRunService = mock(MigrationRunService.class);
        service = new VoucherMigrationService(excelReader, repository, migrationRunService, transactionManager(), tempDir);
    }

    @Test
    void voucherHeaderImportStoresMappedHeaderFields() {
        when(repository.voucherHeaderExists("BR", "324786")).thenReturn(false);
        when(repository.findContractIds("HP", "14066")).thenReturn(List.of(10L));
        when(repository.insertHeader(any())).thenReturn(100L);
        VoucherMigrationSummary summary = VoucherMigrationSummary.base();

        service.importHeader(headerRow(), context(List.of()), summary, 99L, true);

        ArgumentCaptor<VoucherMigrationRepository.VoucherHeaderInsert> captor =
            ArgumentCaptor.forClass(VoucherMigrationRepository.VoucherHeaderInsert.class);
        verify(repository).insertHeader(captor.capture());

        VoucherMigrationRepository.VoucherHeaderInsert header = captor.getValue();
        assertThat(header.voucherType()).isEqualTo("BR");
        assertThat(header.voucherTypeDescription()).isEqualTo("Bank Receipt");
        assertThat(header.voucherNumber()).isEqualTo("324786");
        assertThat(header.voucherDate()).isEqualTo(LocalDate.of(2024, 7, 29));
        assertThat(header.systemDate()).isEqualTo(LocalDate.of(2024, 7, 30));
        assertThat(header.voucherAmount()).isEqualByComparingTo("51200");
        assertThat(header.contractId()).isEqualTo(10L);
        assertThat(header.headerControlCode()).isEqualTo("3001");
        assertThat(header.headerControlName()).isEqualTo("DEBTORS A/C");
    }

    @Test
    void voucherDetailImportStoresMappedDetailFields() {
        when(repository.voucherHeaderExists("BR", "324786")).thenReturn(false);
        when(repository.findContractIds("HP", "14066")).thenReturn(List.of(10L));
        when(repository.insertHeader(any())).thenReturn(100L);
        VoucherMigrationSummary summary = VoucherMigrationSummary.base();

        service.importHeader(headerRow(), context(List.of(detailRow("1"))), summary, 99L, true);

        ArgumentCaptor<VoucherMigrationRepository.VoucherDetailInsert> captor =
            ArgumentCaptor.forClass(VoucherMigrationRepository.VoucherDetailInsert.class);
        verify(repository).insertDetail(captor.capture());

        VoucherMigrationRepository.VoucherDetailInsert detail = captor.getValue();
        assertThat(detail.voucherHeaderId()).isEqualTo(100L);
        assertThat(detail.serialNumber()).isEqualTo(1);
        assertThat(detail.ledgerCode()).isEqualTo("3001");
        assertThat(detail.ledgerName()).isEqualTo("DEBTORS A/C");
        assertThat(detail.partyCode()).isEqualTo("13759");
        assertThat(detail.partyName()).isEqualTo("VENKATSWARA RAJU");
        assertThat(detail.creditAmount()).isEqualByComparingTo("13500");
        assertThat(detail.address()).contains("VILLA 377");
    }

    @Test
    void multipleDetailsPerVoucherAreInserted() {
        when(repository.voucherHeaderExists("BR", "324786")).thenReturn(false);
        when(repository.findContractIds("HP", "14066")).thenReturn(List.of(10L));
        when(repository.insertHeader(any())).thenReturn(100L);

        service.importHeader(headerRow(), context(List.of(detailRow("1"), detailRow("2"))), VoucherMigrationSummary.base(), 99L, true);

        verify(repository, org.mockito.Mockito.times(2)).insertDetail(any());
    }

    @Test
    void duplicateVoucherSkipsHeaderAndDetails() {
        when(repository.voucherHeaderExists("BR", "324786")).thenReturn(true);

        VoucherMigrationService.RowOutcome outcome =
            service.importHeader(headerRow(), context(List.of(detailRow("1"))), VoucherMigrationSummary.base(), 99L, true);

        assertThat(outcome).isEqualTo(VoucherMigrationService.RowOutcome.DUPLICATE);
        verify(repository, never()).insertHeader(any());
        verify(repository, never()).insertDetail(any());
    }

    @Test
    void missingContractLogsWarningAndContinues() {
        when(repository.voucherHeaderExists("BR", "324786")).thenReturn(false);
        when(repository.findContractIds("HP", "14066")).thenReturn(List.of());
        when(repository.insertHeader(any())).thenReturn(100L);
        VoucherMigrationSummary summary = VoucherMigrationSummary.base();

        service.importHeader(headerRow(), context(List.of()), summary, 99L, true);

        assertThat(summary.getMissingContracts()).isEqualTo(1);
        verify(migrationRunService).recordDetail(eq(99L), eq(2), eq("BR/324786"), eq("WARNING"), eq("Missing contract for HP/14066"), isNull());
        verify(repository).insertHeader(any());
    }

    @Test
    void missingLedgerLogsWarningAndContinues() {
        when(repository.voucherHeaderExists("BR", "324786")).thenReturn(false);
        when(repository.findContractIds("HP", "14066")).thenReturn(List.of(10L));
        when(repository.insertHeader(any())).thenReturn(100L);
        VoucherMigrationSummary summary = VoucherMigrationSummary.base();
        VoucherMigrationService.VoucherContext context = new VoucherMigrationService.VoucherContext(
            List.of(headerRow()),
            List.of(detailRow("1")),
            Map.of("BR/324786", List.of(detailRow("1"))),
            Map.of(),
            parties()
        );

        service.importHeader(headerRow(), context, summary, 99L, true);

        assertThat(summary.getMissingLedgers()).isEqualTo(2);
        verify(repository).insertDetail(any());
    }

    @Test
    void missingPartyLogsWarningAndContinues() {
        when(repository.voucherHeaderExists("BR", "324786")).thenReturn(false);
        when(repository.findContractIds("HP", "14066")).thenReturn(List.of(10L));
        when(repository.insertHeader(any())).thenReturn(100L);
        VoucherMigrationSummary summary = VoucherMigrationSummary.base();
        VoucherMigrationService.VoucherContext context = new VoucherMigrationService.VoucherContext(
            List.of(headerRow()),
            List.of(detailRow("1")),
            Map.of("BR/324786", List.of(detailRow("1"))),
            ledgers(),
            Map.of()
        );

        service.importHeader(headerRow(), context, summary, 99L, true);

        assertThat(summary.getMissingParties()).isEqualTo(1);
        verify(migrationRunService).recordDetail(eq(99L), eq(3), eq("BR/324786"), eq("WARNING"), eq("Missing party for ledger_code=3001, party_code=13759"), isNull());
        verify(repository).insertDetail(any());
    }

    @Test
    void prepareAndImportUseLocalFilesAndMigrationLogging() throws Exception {
        createSourceFiles();
        stubWorkbookReads();
        stubSchemaColumns();
        when(repository.voucherHeaderExists("BR", "324786")).thenReturn(false);
        when(repository.findContractIds("HP", "14066")).thenReturn(List.of(10L));
        when(repository.insertHeader(any())).thenReturn(100L);
        when(migrationRunService.startRun(any(), any(), any(), anyInt(), any())).thenReturn(99L);

        VoucherMigrationSummary prepare = service.prepareVoucherMigration();
        VoucherMigrationSummary imported = service.importVouchers();

        assertThat(prepare.isSuccess()).isTrue();
        assertThat(prepare.getInserted()).isZero();
        assertThat(imported.isSuccess()).isTrue();
        assertThat(imported.getInserted()).isEqualTo(1);
        verify(migrationRunService).startRun(eq("VOUCHER"), any(), any(), eq(1), eq("LEGACY_MIGRATION"));
        verify(migrationRunService).completeRun(eq(99L), eq(1), eq(0), eq(0), eq("COMPLETED"));
    }

    private void createSourceFiles() throws Exception {
        Files.write(tempDir.resolve(VoucherMigrationService.HEADER_FILE_NAME), new byte[] {1});
        Files.write(tempDir.resolve(VoucherMigrationService.DETAIL_FILE_NAME), new byte[] {1});
        Files.write(tempDir.resolve(VoucherMigrationService.LEDGER_FILE_NAME), new byte[] {1});
        Files.write(tempDir.resolve(VoucherMigrationService.PARTY_FILE_NAME), new byte[] {1});
        Files.write(tempDir.resolve(VoucherMigrationService.MAPPING_FILE_NAME), new byte[] {1});
    }

    private void stubWorkbookReads() {
        when(excelReader.read(any(InputStream.class), eq(VoucherMigrationService.HEADER_SHEET_NAME), anySet())).thenReturn(List.of(headerRow()));
        when(excelReader.read(any(InputStream.class), eq(VoucherMigrationService.DETAIL_SHEET_NAME), anySet())).thenReturn(List.of(detailRow("1")));
        when(excelReader.read(any(InputStream.class), eq(VoucherMigrationService.LEDGER_SHEET_NAME), anySet())).thenReturn(List.of(ledgerRow()));
        when(excelReader.read(any(InputStream.class), eq(VoucherMigrationService.PARTY_SHEET_NAME), anySet())).thenReturn(List.of(partyRow()));
    }

    private void stubSchemaColumns() {
        when(repository.tableColumns("voucher_headers")).thenReturn(Set.of(
            "voucher_header_id", "voucher_type", "voucher_number", "voucher_date", "system_date",
            "transaction_type", "voucher_amount", "receipt_number", "temporary_receipt_number",
            "temporary_receipt_date", "contract_number", "contract_type", "contract_id", "bank_code",
            "header_control_code", "header_control_name", "remarks", "created_at", "updated_at"
        ));
        when(repository.tableColumns("voucher_details")).thenReturn(Set.of(
            "voucher_detail_id", "voucher_header_id", "serial_number", "category", "ledger_code",
            "ledger_name", "sub_ledger_code", "debit_amount", "credit_amount", "party_code",
            "party_name", "loan_reference", "narration", "address", "created_at", "updated_at"
        ));
    }

    private VoucherMigrationService.VoucherContext context(List<VoucherSourceRow> details) {
        return new VoucherMigrationService.VoucherContext(
            List.of(headerRow()),
            details,
            Map.of("BR/324786", details),
            ledgers(),
            parties()
        );
    }

    private Map<String, String> ledgers() {
        return Map.of("3001", "DEBTORS A/C");
    }

    private Map<String, VoucherMigrationService.PartyLookup> parties() {
        return Map.of("3001/13759", new VoucherMigrationService.PartyLookup("VENKATSWARA RAJU", "VILLA 377, CHENNAI"));
    }

    private VoucherSourceRow headerRow() {
        Map<String, Object> values = new HashMap<>();
        values.put("VOUCHER_TYPE", "BR");
        values.put("VOUCHER_NO", "324786");
        values.put("VOUCHER_DATE", LocalDate.of(2024, 7, 29));
        values.put("STAMP_DATE", LocalDate.of(2024, 7, 30));
        values.put("VR_AMT", new BigDecimal("51200"));
        values.put("CONT_NO", "14066");
        values.put("CONT_TYPE", "HP");
        values.put("PRNO", "97496");
        values.put("TR_NO", "235880");
        values.put("TR_DT", LocalDate.of(2024, 7, 29));
        values.put("BANK_CODE", "B0054");
        values.put("CHEQ_NO", "CHQ-1");
        values.put("HDR_AC_CD", "3001");
        return new VoucherSourceRow(2, values);
    }

    private VoucherSourceRow detailRow(String serialNumber) {
        Map<String, Object> values = new HashMap<>();
        values.put("VOUCHER_TYPE", "BR");
        values.put("VOUCHER_NO", "324786");
        values.put("SL_NO", new BigDecimal(serialNumber));
        values.put("CATEGORY", "HP");
        values.put("AC_CD", "3001");
        values.put("AC_SBCD", "13759");
        values.put("NARR_1", "EMI - WELFUND");
        values.put("DEBIT_AMT", BigDecimal.ZERO);
        values.put("CREDIT_AMT", new BigDecimal("13500"));
        values.put("PARTY_NAME", "VENKATSWARA RAJU");
        values.put("PTY_ADDR1", "VILLA 377");
        values.put("PTY_ADDR2", "OMR");
        values.put("PTY_ADDR3", "PADUR");
        values.put("PTY_CITY", "CHENNAI");
        values.put("PTY_PIN", "603103");
        values.put("PTY_PH", "044");
        return new VoucherSourceRow(3, values);
    }

    private VoucherSourceRow ledgerRow() {
        return new VoucherSourceRow(2, Map.of("AC_CD", "3001", "AC_DESC", "DEBTORS A/C"));
    }

    private VoucherSourceRow partyRow() {
        Map<String, Object> values = new HashMap<>();
        values.put("AC_CD", "3001");
        values.put("PARTY_CODE", "13759");
        values.put("PARTY_NAME", "VENKATSWARA RAJU");
        values.put("PTY_ADDR1", "VILLA 377");
        values.put("PTY_ADDR2", "OMR");
        values.put("PTY_ADDR3", "PADUR");
        values.put("PTY_CITY", "CHENNAI");
        values.put("PTY_PIN", "603103");
        return new VoucherSourceRow(2, values);
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
