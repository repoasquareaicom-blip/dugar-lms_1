package dugar_lms_api.modules.reports.repaymentschedule;

import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static dugar_lms_api.modules.reports.repaymentschedule.RepaymentScheduleData.*;

class RepaymentScheduleTest {
    final ContractAccessRepository access = mock(ContractAccessRepository.class);
    final RepaymentScheduleRepository repository = mock(RepaymentScheduleRepository.class);
    final RepaymentScheduleService service = new RepaymentScheduleService(access, repository);
    final RepaymentSchedulePdf pdf = new RepaymentSchedulePdf("DUGAR FINANCE & INVESTMENTS LIMITED");
    final TestingAuthenticationToken user = new TestingAuthenticationToken("user", "", "ROLE_USER");
    final Header reportHeader = new Header(6535L, "17270", "Test Borrower", LocalDate.of(2026, 10, 5),
        new BigDecimal("300000.00"), new BigDecimal("24.9970"), 24, LocalDate.of(2026, 11, 5));
    final Row row = new Row(24, LocalDate.of(2028, 10, 5), new BigDecimal("15684.42"), new BigDecimal("16011.00"),
        new BigDecimal("326.72"), new BigDecimal("15684.28"), new BigDecimal("0.14"), BigDecimal.ZERO);
    void allow() {
        when(access.canAccess(eq(6535L), any())).thenReturn(true);
        when(repository.header(6535)).thenReturn(Optional.of(reportHeader));
    }
    void ready() {
        allow(); when(repository.totals("17270")).thenReturn(new Totals(row.emiAmount(), row.interestAmount(), row.principalAmount()));
        when(repository.schedule("17270")).thenReturn(List.of(row));
        when(repository.slabs(6535)).thenReturn(List.of(new Slab(1, 24, new BigDecimal("16011.00"))));
    }
    org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new RepaymentScheduleController(service, pdf)).build();
    }
    @Test void unauthenticatedRequestDoesNotReadContracts() throws Exception {
        mvc().perform(get("/api/contracts/6535/repayment-schedule/pdf"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(access, repository);
    }
    @Test void inaccessibleContractDoesNotReadFinancialData() throws Exception {
        mvc().perform(get("/api/contracts/6535/repayment-schedule/pdf").principal(user))
            .andExpect(status().isNotFound());
        verifyNoInteractions(repository);
    }
    @Test void missingContractIs404() throws Exception {
        when(access.canAccess(eq(6535L), any())).thenReturn(true);
        when(repository.header(6535)).thenReturn(Optional.empty());
        mvc().perform(get("/api/contracts/6535/repayment-schedule/pdf").principal(user)).andExpect(status().isNotFound());
        verify(repository, never()).schedule(any());
    }
    @Test void resolvesNumberAndPassesThroughSqlValues() {
        ready(); assertThat(service.load(6535, user).rows()).containsExactly(row);
        var order = inOrder(access, repository);
        order.verify(access).canAccess(eq(6535L), any());
        order.verify(repository).header(6535); order.verify(repository).schedule("17270");
    }
    @Test void unsupportedSqlScheduleReturns422Json() throws Exception {
        allow(); when(repository.schedule("17270")).thenThrow(new UncategorizedSQLException("report", "query",
            new SQLException("ERROR: Unsupported repayment frequency\nCONTEXT: private SQL", "22023")));
        mvc().perform(get("/api/contracts/6535/repayment-schedule/pdf").principal(user))
            .andExpect(status().isUnprocessableEntity()).andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.message").value("Unsupported repayment frequency"));
    }
    @Test void unavailableFunctionReturns503() throws Exception {
        allow(); when(repository.schedule("17270")).thenThrow(new UncategorizedSQLException("report", "query",
            new SQLException("undefined function", "42883")));
        mvc().perform(get("/api/contracts/6535/repayment-schedule/pdf").principal(user)).andExpect(status().isServiceUnavailable());
    }
    @Test void emptyScheduleReturns422() throws Exception {
        allow(); when(repository.schedule("17270")).thenReturn(List.of());
        mvc().perform(get("/api/contracts/6535/repayment-schedule/pdf").principal(user)).andExpect(status().isUnprocessableEntity());
    }
    @Test void endpointReturnsInlinePdfWithNoCache() throws Exception {
        ready(); var response = mvc().perform(get("/api/contracts/6535/repayment-schedule/pdf").principal(user))
            .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", "inline; filename=\"Repayment_Schedule_17270.pdf\""))
            .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        assertThat(new String(response.getContentAsByteArray(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
    @Test void pdfEmbedsLogoFontLandscapeAndUnchangedAmounts() throws Exception {
        // Deliberately inconsistent values prove the renderer does not amortise/rebalance.
        var unchanged = new Row(24, row.dueDate(), row.openingPrincipal(), row.emiAmount(),
            new BigDecimal("123.45"), new BigDecimal("99.99"), new BigDecimal("-0.14"), BigDecimal.ZERO);
        try (var document = Loader.loadPDF(pdf.generate(new RepaymentScheduleData(reportHeader, List.of(), List.of(unchanged))))) {
            assertThat(new PDFTextStripper().getText(document)).contains("REPAYMENT SCHEDULE", "24.9970%",
                "05-Nov-2026", "₹123", "₹100", "₹0", "Page 1 of 1")
                .doesNotContain("DUGAR FINANCE & INVESTMENTS LIMITED", "Amounts in INR", "Final balance is shown");
            assertThat(document.getPage(0).getMediaBox().getWidth()).isGreaterThan(document.getPage(0).getMediaBox().getHeight());
            var resources = document.getPage(0).getResources(); boolean image = false;
            for (var name : resources.getXObjectNames()) image |= resources.getXObject(name) instanceof PDImageXObject;
            assertThat(image).isTrue();
            for (var name : resources.getFontNames()) assertThat(resources.getFont(name).isEmbedded()).isTrue();
        }
    }
    @Test void multipageRepeatsHeadersAndNumbers() throws Exception {
        var rows = java.util.stream.IntStream.rangeClosed(1, 70).mapToObj(n ->
            new Row(n, row.dueDate(), row.openingPrincipal(), row.emiAmount(), row.interestAmount(),
                row.principalAmount(), row.closingPrincipal(), BigDecimal.ZERO)).toList();
        var slabs = List.of(new Slab(1, 12, new BigDecimal("12000.00")),
            new Slab(2, 35, new BigDecimal("15000.00")), new Slab(3, 1, new BigDecimal("18000.00")),
            new Slab(4, 22, new BigDecimal("19000.00")));
        try (var document = Loader.loadPDF(pdf.generate(new RepaymentScheduleData(reportHeader, slabs, rows)))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                var stripper = new PDFTextStripper(); stripper.setStartPage(i); stripper.setEndPage(i);
                String content = stripper.getText(document);
                assertThat(content).contains("REPAYMENT SCHEDULE", "EMI No.", "Closing Principal", "Page " + i + " of " + document.getNumberOfPages());
                if (i == 1) {
                    assertThat(content).contains("EMI STRUCTURE", "1–12: ₹12,000", "13–47: ₹15,000",
                        "48: ₹18,000", "49–70: ₹19,000");
                } else {
                    assertThat(content).doesNotContain("BORROWER NAME", "EMI STRUCTURE", "CONTRACT NUMBER");
                }
                if (i < document.getNumberOfPages()) assertThat(content).doesNotContainPattern("(?m)^TOTAL(?! INSTALLMENTS)(?:\\s|$)");
                else assertThat(content).contains("TOTAL");
            }
        }
    }
    @Test void singleSlabPreserves17276PaymentsTotalAndNegativeClosingBalance() throws Exception {
        var rows = java.util.stream.IntStream.rangeClosed(1, 48).mapToObj(n ->
            new Row(n, row.dueDate(), row.openingPrincipal(), new BigDecimal("14423.00"),
                row.interestAmount(), row.principalAmount(), new BigDecimal("-0.82"), BigDecimal.ZERO)).toList();
        var header = new Header(6536L, "17276", "Test Borrower", reportHeader.contractDate(),
            reportHeader.loanAmount(), reportHeader.irrRate(), 48, reportHeader.firstEmiDate());
        try (var document = Loader.loadPDF(pdf.generate(new RepaymentScheduleData(header,
                List.of(new Slab(1, 48, new BigDecimal("14423.00"))), rows)))) {
            assertThat(new PDFTextStripper().getText(document))
                .contains("48 × ₹14,423", "₹6,92,304", "-₹1");
            var stripper = new PDFTextStripper() {
                @Override protected void processTextPosition(org.apache.pdfbox.text.TextPosition position) {
                    assertThat(position.getXDirAdj()).isGreaterThanOrEqualTo(71.9f);
                    assertThat(position.getXDirAdj() + position.getWidthDirAdj())
                        .isLessThanOrEqualTo(document.getPage(0).getMediaBox().getWidth() - 71.9f);
                    super.processTextPosition(position);
                }
            };
            stripper.getText(document);
        }
    }
    @Test void longBorrowerAndManySlabsWrapWithoutDroppingContent() throws Exception {
        String borrower = "A borrower with a long registered name that must wrap across several lines in its column";
        var header = new Header(reportHeader.contractId(), "17270", borrower, reportHeader.contractDate(),
            reportHeader.loanAmount(), reportHeader.irrRate(), 40, reportHeader.firstEmiDate());
        var slabs = java.util.stream.IntStream.rangeClosed(1, 40)
            .mapToObj(n -> new Slab(n, 1, new BigDecimal("12000.00"))).toList();
        try (var document = Loader.loadPDF(pdf.generate(new RepaymentScheduleData(header, slabs, List.of(row))))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String content = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(content).contains(borrower, "1: ₹12,000", "40: ₹12,000", "TOTAL", "₹0");
        }
    }
    @Test void indianFormattingRoundsWholeRupeesLikePostgres() {
        assertThat(RepaymentSchedulePdf.money(new BigDecimal("299999.86"))).isEqualTo("\u20b93,00,000");
        assertThat(RepaymentSchedulePdf.money(new BigDecimal("-0.14"))).isEqualTo("\u20b90");
        assertThat(RepaymentSchedulePdf.money(new BigDecimal("-0.5"))).isEqualTo("-\u20b91");
        assertThat(RepaymentSchedulePdf.money(new BigDecimal("0.5"))).isEqualTo("\u20b91");
    }
    @Test void totalsRoundOnceFromPrecisionInsteadOfDisplayedRows() throws Exception {
        var rounded = new Row(1, row.dueDate(), new BigDecimal("100"), new BigDecimal("51"),
            BigDecimal.ONE, new BigDecimal("50"), new BigDecimal("50"), BigDecimal.ZERO);
        var totals = new Totals(new BigDecimal("101.02"), new BigDecimal("1.02"), new BigDecimal("100"));
        try (var document = Loader.loadPDF(pdf.generate(new RepaymentScheduleData(reportHeader,
                List.of(), List.of(rounded, rounded), totals)))) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("\u20b9101", "TOTAL").doesNotContain("\u20b9102", "\u20b92");
        }
    }
}
