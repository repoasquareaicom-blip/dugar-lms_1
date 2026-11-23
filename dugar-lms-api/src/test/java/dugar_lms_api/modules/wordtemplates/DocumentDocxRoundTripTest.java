package dugar_lms_api.modules.wordtemplates;

import dugar_lms_api.modules.wordtemplates.service.WordDocxTemplateEngine;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentDocxRoundTripTest {
    @TempDir Path directory;

    @Test
    void stillMergesDocxParagraphsAndTablesIncludingSplitRuns() throws Exception {
        Path template = directory.resolve("template.docx");
        Path output = directory.resolve("generated-document.docx");
        try (var doc = new XWPFDocument(); var stream = Files.newOutputStream(template)) {
            var paragraph = doc.createParagraph();
            var run=paragraph.createRun();run.setBold(true);run.setFontFamily("Nirmala UI");run.setText("Dear {{BOR");
            paragraph.createRun().setText("ROWER_NAME}}");
            doc.createTable(1, 1).getRow(0).getCell(0).setText("{{CONTRACT_NUMBER}}");
            doc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Header {{BORROWER_NAME}}");
            doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Static footer");
            doc.createParagraph().createRun().addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
            doc.write(stream);
        }
        var engine = new WordDocxTemplateEngine();
        assertThat(engine.extractPlaceholders(template)).containsExactlyInAnyOrder("{{BORROWER_NAME}}", "{{CONTRACT_NUMBER}}");
        engine.merge(template, output, Map.of("{{BORROWER_NAME}}", "Test Borrower", "{{CONTRACT_NUMBER}}", "TEST-001"));
        try (var stream = Files.newInputStream(output); var doc = new XWPFDocument(stream)) {
            assertThat(doc.getParagraphs().getFirst().getText()).isEqualTo("Dear Test Borrower");
            assertThat(doc.getTables().getFirst().getRow(0).getCell(0).getText()).isEqualTo("TEST-001");
            assertThat(doc.getParagraphs().getFirst().getRuns().getFirst().isBold()).isTrue();
            assertThat(doc.getParagraphs().getFirst().getRuns().getFirst().getFontFamily()).isEqualTo("Nirmala UI");
            assertThat(doc.getHeaderList().getFirst().getText()).contains("Header Test Borrower");
            assertThat(doc.getFooterList().getFirst().getText()).contains("Static footer");
            assertThat(doc.getParagraphs().getLast().getCTP().xmlText()).contains("page");
        }
        assertThat(engine.extractPlaceholders(output)).isEmpty();
    }
}
