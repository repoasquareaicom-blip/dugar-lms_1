package dugar_lms_api.modules.reports.stockstatement;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockStatementTest {
    private final StockStatementRepository repository = mock(StockStatementRepository.class);
    private final StockStatementService service = new StockStatementService(repository);
    private final ReportAccessScope scope = new ReportAccessScope(true, 1L, "admin");

    private StockStatementRow row() {
        return new StockStatementRow(1L, "L-100", LocalDate.of(2025, 1, 1),
            "Borrower\nAddress 1\nAddress 2\nCity\nState / 600001", "HYUNDAI XCENT VTVT SX\nRJ01CD2898",
            new BigDecimal("100000.00"), new BigDecimal("10000.00"), 10, 3, new BigDecimal("25000.00"));
    }

    @Test
    void usesBackendKolkataDateAndSumsProcedureValues() {
        LocalDate before = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        when(repository.activeBank(1L)).thenReturn(new StockStatementRepository.Bank(1L, "B1", "Bank One"));
        when(repository.rows(eq(1L), any(), eq(scope))).thenReturn(List.of(row(), row()));
        var report = service.generate(1L, scope);
        assertThat(report.businessDate()).isBetween(before, LocalDate.now(ZoneId.of("Asia/Kolkata")));
        verify(repository).rows(1L, report.businessDate(), scope);
        assertThat(report.contractCount()).isEqualTo(2);
        assertThat(report.totalLoanAmount()).isEqualByComparingTo("200000");
        assertThat(report.totalPrincipalOutstanding()).isEqualByComparingTo("50000");
        assertThat(report.fingerprint()).isEqualTo(service.generate(1L, scope).fingerprint());
        when(repository.rows(eq(1L), any(), eq(scope))).thenReturn(List.of(row()));
        assertThat(service.generate(1L, scope).fingerprint()).isNotEqualTo(report.fingerprint());
    }

    @Test
    void historicalDateControlsProcedureDataAndFingerprint() {
        var date = LocalDate.of(2026, 9, 30);
        when(repository.activeBank(1L)).thenReturn(new StockStatementRepository.Bank(1L, "B1", "Renamed bank"));
        when(repository.rows(1L, date, scope)).thenReturn(List.of(row()));
        var report = service.generate(1L, date, scope);
        assertThat(report.businessDate()).isEqualTo(date);
        assertThat(report.bankName()).isEqualTo("Renamed bank");
        verify(repository).rows(1L, date, scope);
        when(repository.rows(1L, date.minusDays(1), scope)).thenReturn(List.of(row()));
        assertThat(service.generate(1L, date.minusDays(1), scope).fingerprint()).isNotEqualTo(report.fingerprint());
        assertThatIllegalArgumentException().isThrownBy(() -> service.generate(1L, null, scope));
        assertThat(StockStatementRepository.REPORT_SQL).contains("bank.id::text = cd.stock_marked_to_bank")
            .doesNotContain("TRIM(cd.stock_marked_to_bank)");
    }

    @Test
    void rejectsInvalidBanks() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.generate(null, scope));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generate(0L, scope));
        when(repository.activeBank(3L)).thenThrow(new IllegalArgumentException("Select an active bank."));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generate(3L, scope));
        verify(repository, never()).rows(any(), any(), any());
    }

    @Test
    void existingProcedureAndBankFilteredSelectUseSameConnectionAndBackendDate() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var connection = mock(Connection.class);
        var call = mock(PreparedStatement.class);
        var select = mock(PreparedStatement.class);
        var result = mock(ResultSet.class);
        when(connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?, ?)" )).thenReturn(call);
        when(connection.prepareStatement(StockStatementRepository.REPORT_SQL)).thenReturn(select);
        when(select.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);
        when(jdbc.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<List<StockStatementRow>>>any()))
            .thenAnswer(invocation -> {
                ConnectionCallback<List<StockStatementRow>> callback = invocation.getArgument(0);
                return callback.doInConnection(connection);
            });
        var date = LocalDate.of(2026, 10, 15);
        assertThat(new StockStatementRepository(jdbc).rows(5L, date, scope)).isEmpty();
        verify(call).setObject(1, date);
        verify(call).setString(2, null);
        verify(call).setString(3, "admin");
        verify(call).execute();
        verify(select).setObject(1, date);
        verify(select).setLong(2, 5L);
        verify(select).setBoolean(3, true);
        verify(select).setObject(4, 1L);
        verify(call).close();
        verify(select).close();
        verify(result).close();
    }

    @Test
    void emptyReportExportsOnlyHeadingsWithoutFooterTotals() throws Exception {
        var report = new StockStatementResponse(1L, "B1", "Bank", LocalDate.of(2026, 10, 15), List.of(),
            0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "empty");
        var output = new ByteArrayOutputStream();
        new StockStatementExcel().write(report, output);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(3);
            assertThat((Object) workbook.getSheetAt(0).getRow(4)).isNull();
        }
    }

    @Test
    void workbookMatchesPreviewWithNumericDatesWrappingAndNoFooterTotals() throws Exception {
        var report = new StockStatementResponse(1L, "B1", "Bank One", LocalDate.of(2026, 10, 15), List.of(row()),
            1, row().loanAmount(), row().principalPerInstallment(), row().principalOutstanding(), "fingerprint");
        var output = new ByteArrayOutputStream();
        new StockStatementExcel().write(report, output);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("DUGAR FINANCE & INVESTMENTS LIMITED");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("STOCK STATEMENT AS ON 15.10.2026");
            assertThat(sheet.getNumMergedRegions()).isEqualTo(3);
            for (int col = 0; col < 9; col++) assertThat(sheet.getRow(3).getCell(col).getStringCellValue()).isEqualTo(StockStatementExcel.HEADINGS[col]);
            var data = sheet.getRow(4);
            assertThat(data.getCell(2).getStringCellValue()).isEqualTo(row().hirerName());
            assertThat(data.getCell(3).getStringCellValue()).isEqualTo(row().vehicleDetails());
            assertThat(data.getCell(2).getCellStyle().getWrapText()).isTrue();
            assertThat(data.getHeightInPoints()).isGreaterThan(80);
            assertThat(data.getCell(1).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(row().contractDate());
            assertThat(data.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("dd/mm/yyyy");
            assertThat(data.getCell(4).getNumericCellValue()).isEqualTo(100000);
            assertThat(data.getCell(8).getNumericCellValue()).isEqualTo(25000);
            assertThat((Object) sheet.getRow(5)).isNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
        }
    }

    @Test
    void addressFollowsDemandListCommaAndPinRulesWithoutDeduplicatingValues() {
        assertThat(StockStatementAddress.format("MAHENDRA KUMAR SHARMA",
            "AJAY NAGAR THANA ROAD,1927/26 CHUNE KA BHATTA,RAJASTHAN,AJMER,RAJASTHAN,305001"))
            .isEqualTo("MAHENDRA KUMAR SHARMA\nAJAY NAGAR THANA ROAD, 1927/26 CHUNE KA BHATTA, RAJASTHAN, AJMER, RAJASTHAN - 305001");
        assertThat(StockStatementAddress.format("Borrower",
            "A/P-133/1 MIRZA PLOT NEAR,LAXMI MANDIR MURUM TAL,UMARGA,DHARASHIV,MAHARASHTRA,413605"))
            .isEqualTo("Borrower\nA/P-133/1 MIRZA PLOT NEAR, LAXMI MANDIR MURUM TAL, UMARGA, DHARASHIV, MAHARASHTRA - 413605");
        assertThat(StockStatementAddress.format(" Borrower ", "Street, ,null, MAHARASHTRA, MAHARASHTRA / CITY, MAHARASHTRA, 413112"))
            .isEqualTo("Borrower\nStreet, MAHARASHTRA, MAHARASHTRA / CITY, MAHARASHTRA - 413112");
        assertThat(StockStatementAddress.format("Borrower", null)).isEqualTo("Borrower");
        assertThat(StockStatementAddress.format(null, " , , ")).isEmpty();
        assertThat(StockStatementAddress.format("Borrower", "Street, UNKNOWN_STATE, 600001"))
            .isEqualTo("Borrower\nStreet, UNKNOWN_STATE - 600001");
    }

    @Test
    void deniesUnauthorizedRequestsAndRejectsChangedPreview() {
        var access = mock(MenuAccessService.class);
        var mockedService = mock(StockStatementService.class);
        var excel = mock(StockStatementExcel.class);
        var controller = new StockStatementController(mockedService, excel, access);
        var authentication = new TestingAuthenticationToken("user", "password");
        assertThatThrownBy(() -> controller.generate(1L, LocalDate.of(2026, 9, 30), authentication)).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(mockedService);
        when(access.canView(anyString(), eq(authentication))).thenReturn(true);
        when(mockedService.generate(eq(1L), eq(LocalDate.of(2026, 9, 30)), any())).thenReturn(new StockStatementResponse(1L, "B1", "Bank", LocalDate.of(2026, 10, 15),
            List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "new"));
        assertThatThrownBy(() -> controller.export(1L, LocalDate.of(2026, 9, 30), "old", authentication, new MockHttpServletResponse()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
        verifyNoInteractions(excel);
    }
}
