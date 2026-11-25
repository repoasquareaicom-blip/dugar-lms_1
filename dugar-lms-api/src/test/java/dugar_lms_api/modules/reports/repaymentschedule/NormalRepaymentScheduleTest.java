package dugar_lms_api.modules.reports.repaymentschedule;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.contracts.service.ContractListService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static dugar_lms_api.modules.reports.repaymentschedule.NormalRepaymentSchedule.*;
import static dugar_lms_api.modules.reports.repaymentschedule.NormalRepaymentScheduleService.build;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NormalRepaymentScheduleTest {
    private static final LocalDate FIRST = LocalDate.of(2025, 1, 31);
    private final NormalRepaymentSchedulePdf pdf = new NormalRepaymentSchedulePdf(
        "DUGAR FINANCE & INVESTMENTS LIMITED", "7th Floor, Dugar Towers, 34 (Old No. 123) Marshalls Road, Egmore, Chennai, Tamil Nadu 600008",
        "+91 93830 07676 / +91 98410 96460");

    @Test void administrativeOfficeCityStartsOnTheNextLine() throws Exception {
        var report = build(details(6, "Monthly", "600"), List.of(slab(1, 6, "100")));
        String street = "Duggar  chambers, No.9, Damodaran Street, Chetpet,";
        String locality = "Chennai, Tamil Nadu - 600031.";
        for (String separator : List.of(" ", "\r\n")) {
            var addressPdf = new NormalRepaymentSchedulePdf("DUGAR FINANCE & INVESTMENTS LIMITED",
                street + separator + locality, "044 28586464 / +91 93830 07676");
            try (var document = Loader.loadPDF(addressPdf.generate(report))) {
                String content = new PDFTextStripper().getText(document);
                assertThat(content.lines().toList()).containsSubsequence(
                    "Administrative office: " + street, locality, "Phone: 044 28586464 / +91 93830 07676");
            }
        }
    }

    @Test void quarterlySlabsUseActualAmountsAndReconcileWholeRupees() {
        var report = build(details(12, "Quarterly", "302.49"), List.of(slab(1, 2, "100.49"), slab(2, 1, "101.51")));
        assertThat(report.installments()).containsExactly(
            new Installment(1, FIRST, new BigDecimal("100")),
            new Installment(2, LocalDate.of(2025, 4, 30), new BigDecimal("100")),
            new Installment(3, LocalDate.of(2025, 7, 31), new BigDecimal("102")));
        var adjusted = build(details(12, "Quarterly", "301.47"), List.of(slab(1, 3, "100.49")));
        assertThat(adjusted.installments()).extracting(Installment::amount)
            .containsExactly(new BigDecimal("101"), new BigDecimal("100"), new BigDecimal("100"));
        assertThat(adjusted.total()).isEqualByComparingTo("301");
        assertThatThrownBy(() -> build(details(12, "Quarterly", "302.00"), List.of(slab(1, 3, "100.49"))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("do not reconcile");
        assertThatThrownBy(() -> build(details(12, null, "300"), List.of(slab(1, 3, "100"))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("frequency is missing");
        assertThatThrownBy(() -> build(details(12, "Bullet", "300"), List.of(slab(1, 3, "100"))))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("individual contractual due dates");
    }

    @Test void matchesPreferredTableSizesAndPaginatesWithoutDroppingRows() throws Exception {
        int[] counts = {6, 12, 18, 24, 34, 36, 48, 60};
        int[] rowsPerTable = {6, 12, 9, 12, 12, 12, 16, 20};
        for (int i = 0; i < counts.length; i++) {
            assertThat(NormalRepaymentSchedulePdf.rowsPerTable(counts[i], 20)).isEqualTo(rowsPerTable[i]);
        }
        var report = build(details(34, "Monthly", "365500"), List.of(slab(1, 34, "10750")));
        try (var document = Loader.loadPDF(pdf.generate(report))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            var page = document.getPage(0).getMediaBox();
            assertThat(page.getHeight()).isGreaterThan(page.getWidth());
            String content = new PDFTextStripper().getText(document);
            assertThat(content).contains("Example Borrower", "Example Parent", "Tamil Nadu", "Example Vehicle",
                "31-01-2025", "28-02-2025", "31-03-2025", "3,65,500", "AUTHORISED SIGNATORY", "Insurance Deposit")
                .doesNotContain("Opening Principal", "Closing Principal", "undefined");
            assertThat(content.split("Due Amount", -1)).hasSize(4);
            var stripper = new PDFTextStripper() {
                @Override protected void processTextPosition(org.apache.pdfbox.text.TextPosition position) {
                    assertThat(position.getXDirAdj()).isGreaterThanOrEqualTo(71.9f);
                    assertThat(position.getXDirAdj() + position.getWidthDirAdj()).isLessThanOrEqualTo(page.getWidth() - 71.9f);
                    super.processTextPosition(position);
                }
            };
            stripper.getText(document);
            var directory = Path.of("target/repayment-schedule-preview");
            Files.createDirectories(directory);
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, 130), "png", directory.resolve("normal-34.png").toFile());
        }
        var longReport = build(details(150, "Monthly", "1612500"), List.of(slab(1, 150, "10750")));
        try (var document = Loader.loadPDF(pdf.generate(longReport))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            var stripper = new PDFTextStripper();
            int number = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String content = stripper.getText(document);
                assertThat(content).contains("S.No", "Due Amount", "Page " + page);
                var rows = java.util.regex.Pattern.compile("(?m)^(\\d+)\\s+\\d{2}-\\d{2}-\\d{4}").matcher(content);
                while (rows.find()) assertThat(Integer.parseInt(rows.group(1))).isEqualTo(++number);
                if (page < document.getNumberOfPages()) assertThat(content).doesNotContain("AUTHORISED SIGNATORY");
                else assertThat(content).contains("AUTHORISED SIGNATORY");
            }
            assertThat(number).isEqualTo(150);
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(document.getNumberOfPages() - 1, 120), "png",
                Path.of("target/repayment-schedule-preview/normal-last.png").toFile());
        }
    }

    @Test void eachLookupRequiresItsOwnMenuAndNormalPdfRequiresPermissionBeforeContractAccess() throws Exception {
        var access = mock(MenuAccessService.class);
        var contracts = mock(ContractListService.class);
        var normal = mock(NormalRepaymentScheduleService.class);
        var user = new TestingAuthenticationToken("user", "", "ROLE_USER");
        var mvc = MockMvcBuilders.standaloneSetup(new RepaymentSchedulePagesController(access, contracts, normal, pdf)).build();
        String root = "/api/contracts/repayment-schedules/";
        mvc.perform(get(root + "normal/contracts").param("keyword", "17")).andExpect(status().isUnauthorized());
        when(access.canView(RepaymentSchedulePagesController.WITH_INTEREST, user)).thenReturn(true);
        mvc.perform(get(root + "with-interest/contracts").param("keyword", "17").principal(user)).andExpect(status().isOk());
        verify(contracts).getLoanReferenceOptions("17", 15, user);
        mvc.perform(get(root + "normal/contracts").param("keyword", "17407").principal(user)).andExpect(status().isForbidden());
        String normalPdf = "/api/contracts/42/repayment-schedule/normal/pdf";
        mvc.perform(get(normalPdf).principal(user)).andExpect(status().isForbidden());
        verifyNoInteractions(normal);
        when(access.canView(RepaymentSchedulePagesController.NORMAL, user)).thenReturn(true);
        mvc.perform(get(root + "normal/contracts").param("keyword", "17407").principal(user)).andExpect(status().isOk());
        verify(contracts).getLoanReferenceOptions("17407", 15, user);
        when(normal.load(42, user)).thenReturn(build(details(1, "Bullet", "400"), List.of(slab(1, 1, "400"))));
        mvc.perform(get(normalPdf).principal(user)).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    private static RepaymentScheduleData.Slab slab(int sequence, int count, String amount) {
        return new RepaymentScheduleData.Slab(sequence, count, new BigDecimal(amount));
    }

    private static Details details(int tenure, String frequency, String total) {
        return new Details("TEST-17407", FIRST.minusMonths(1), tenure, FIRST, frequency, null,
            "Example Vehicle", "Example Borrower", "Example Parent", "10 Example Road", null, "Egmore", "Chennai",
            "Tamil Nadu", "600008", new BigDecimal("260000"), new BigDecimal("105500"), null,
            new BigDecimal("1250"), new BigDecimal(total));
    }
}
