package dugar_lms_api.modules.wordtemplates.service;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WordDocxTemplateEngine {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[A-Z0-9_]+}}");

    public Set<String> extractPlaceholders(Path path) {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            LinkedHashSet<String> placeholders = new LinkedHashSet<>();
            visitDocument(document, paragraph -> extractFromParagraph(paragraph, placeholders));
            return placeholders;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read DOCX placeholders.");
        }
    }

    public void merge(Path templatePath, Path outputPath, Map<String, String> values) {
        try (InputStream inputStream = Files.newInputStream(templatePath);
             XWPFDocument document = new XWPFDocument(inputStream);
             OutputStream outputStream = Files.newOutputStream(outputPath)) {
            visitDocument(document, paragraph -> replaceInParagraph(paragraph, values));
            document.write(outputStream);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to generate Word document.");
        }
    }

    private void visitDocument(XWPFDocument document, ParagraphConsumer consumer) {
        visitBodyElements(document.getBodyElements(), consumer);
        document.getHeaderList().forEach(header -> visitHeaderFooter(header, consumer));
        document.getFooterList().forEach(footer -> visitHeaderFooter(footer, consumer));
    }

    private void visitHeaderFooter(XWPFHeaderFooter headerFooter, ParagraphConsumer consumer) {
        headerFooter.getParagraphs().forEach(consumer::accept);
        headerFooter.getTables().forEach(table -> visitTable(table, consumer));
    }

    private void visitBodyElements(List<IBodyElement> elements, ParagraphConsumer consumer) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph paragraph) {
                consumer.accept(paragraph);
            } else if (element instanceof XWPFTable table) {
                visitTable(table, consumer);
            }
        }
    }

    private void visitTable(XWPFTable table, ParagraphConsumer consumer) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                visitBodyElements(cell.getBodyElements(), consumer);
            }
        }
    }

    private void extractFromParagraph(XWPFParagraph paragraph, Set<String> placeholders) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(paragraph.getText());
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> values) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        List<RunSpan> spans = new ArrayList<>();
        for (XWPFRun run : runs) {
            String runText = run.text();
            int start = text.length();
            text.append(runText == null ? "" : runText);
            spans.add(new RunSpan(run, start, text.length()));
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        List<Replacement> replacements = new ArrayList<>();
        while (matcher.find()) {
            String replacement = values.get(matcher.group());
            if (replacement != null) {
                replacements.add(new Replacement(matcher.start(), matcher.end(), replacement));
            }
        }
        for (int index = replacements.size() - 1; index >= 0; index--) {
            applyReplacement(spans, replacements.get(index));
        }
    }

    private void applyReplacement(List<RunSpan> spans, Replacement replacement) {
        boolean written = false;
        for (RunSpan span : spans) {
            if (span.end() <= replacement.start() || span.start() >= replacement.end()) {
                continue;
            }
            String current = span.run().text();
            if (current == null) {
                current = "";
            }
            int localStart = Math.max(0, replacement.start() - span.start());
            int localEnd = Math.min(current.length(), replacement.end() - span.start());
            String next = current.substring(0, localStart) + (written ? "" : replacement.text()) + current.substring(localEnd);
            span.run().setText(next, 0);
            written = true;
        }
    }

    private record RunSpan(XWPFRun run, int start, int end) {
    }

    private record Replacement(int start, int end, String text) {
    }

    private interface ParagraphConsumer {
        void accept(XWPFParagraph paragraph);
    }
}
