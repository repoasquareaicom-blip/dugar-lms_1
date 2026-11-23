package dugar_lms_api.modules.reports.repaymentschedule;

import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.Map;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "REPAYMENT_SCHEDULE_TEST_URL", matches = ".+")
class RepaymentSchedulePostgresTest {
    @Test void generatesActualSql17270PdfAndMultipagePreviewWithoutChangingRecords() throws Exception {
        String url = System.getenv("REPAYMENT_SCHEDULE_TEST_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/repayment_schedule_stage1_test")) {
            throw new IllegalArgumentException("Disposable local test database required");
        }
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", ""));
        var repository = new RepaymentScheduleRepository(jdbc);
        var service = new RepaymentScheduleService(new ContractAccessRepository(new NamedParameterJdbcTemplate(jdbc)), repository);
        var user = new TestingAuthenticationToken("local-test", "", "ROLE_USER");
        user.setDetails(Map.of("userGroup", "admin", "userId", 1L));
        var before = jdbc.queryForList("SELECT to_jsonb(c)::text AS value FROM public.contracts c ORDER BY contract_id");
        var beforeSlabs = jdbc.queryForList("SELECT to_jsonb(r)::text AS value FROM public.contract_repayment_structures r ORDER BY repayment_structure_id");
        var data = service.load(6535, user);
        assertThat(data.rows()).hasSize(24);
        assertThat(data.header().irrRate()).isEqualByComparingTo("24.9970");
        assertThat(data.rows().stream().map(r -> r.emiAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("384264.00");
        assertThat(data.totals().interest().setScale(0, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("84264");
        assertThat(data.totals().principal().setScale(0, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("300000");
        assertThat(data.rows().getLast().closingPrincipal()).isEqualByComparingTo("0");
        var pdf = new RepaymentSchedulePdf("DUGAR FINANCE & INVESTMENTS LIMITED");
        Path directory = Path.of("target/repayment-schedule-preview"); Files.createDirectories(directory);
        byte[] bytes = pdf.generate(data); Files.write(directory.resolve("repayment-schedule-17270.pdf"), bytes);
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(new PDFTextStripper().getText(document)).contains("₹3,84,264", "₹84,264", "₹3,00,000", "₹0", "24.9970%", "Test Borrower 17270");
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, 130), "png", directory.resolve("repayment-schedule-17270.png").toFile());
        }
        var longRows = new java.util.ArrayList<RepaymentScheduleData.Row>();
        for (int i = 0; i < 3; i++) longRows.addAll(data.rows());
        byte[] multi = pdf.generate(new RepaymentScheduleData(data.header(), data.slabs(), longRows));
        Files.write(directory.resolve("repayment-schedule-multipage.pdf"), multi);
        try (var document = Loader.loadPDF(multi)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(document.getNumberOfPages() - 1, 130), "png", directory.resolve("repayment-schedule-last-page.png").toFile());
        }
        assertThat(jdbc.queryForList("SELECT to_jsonb(c)::text AS value FROM public.contracts c ORDER BY contract_id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT to_jsonb(r)::text AS value FROM public.contract_repayment_structures r ORDER BY repayment_structure_id")).isEqualTo(beforeSlabs);
        user.setDetails(Map.of("userGroup", "user", "userId", 2L));
        assertThatThrownBy(() -> service.load(6535, user)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("404");
    }
}
