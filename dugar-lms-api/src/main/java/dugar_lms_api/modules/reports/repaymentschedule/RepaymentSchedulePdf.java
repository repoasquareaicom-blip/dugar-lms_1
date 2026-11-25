package dugar_lms_api.modules.reports.repaymentschedule;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static dugar_lms_api.modules.reports.repaymentschedule.RepaymentScheduleData.*;

@Component
public class RepaymentSchedulePdf {
    // Existing legal name from StockStatementExcel; configurable without inventing a new identity.
    private final String companyName;
    private static final float MARGIN = 72, TOP = 42, ROW_HEIGHT = 13, BOTTOM = 60;
    private static final float CONTENT_WIDTH = PDRectangle.A4.getHeight() - 2 * MARGIN;
    private static final float AMOUNT_WIDTH = (CONTENT_WIDTH - 118) / 5;
    private static final float[] WIDTHS = {42, 76, AMOUNT_WIDTH, AMOUNT_WIDTH, AMOUNT_WIDTH, AMOUNT_WIDTH, AMOUNT_WIDTH};
    private static final Color BLUE = new Color(23, 54, 93), MUTED = new Color(100, 116, 139);
    private static final Color PANEL = new Color(245, 248, 252), BORDER = new Color(217, 227, 239);
    private static final Color STRIP = new Color(232, 241, 250);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    public RepaymentSchedulePdf(@Value("${app.company-name:DUGAR FINANCE & INVESTMENTS LIMITED}") String companyName) {
        this.companyName = companyName;
    }

    public byte[] generate(RepaymentScheduleData report) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream();
             var fontInput = new ClassPathResource("reports/fonts/NotoSans-Regular.ttf").getInputStream();
             var logoInput = new ClassPathResource("reports/images/dugar-logo.png").getInputStream()) {
            var font = PDType0Font.load(document, fontInput, true);
            var logo = PDImageXObject.createFromByteArray(document, logoInput.readAllBytes(), "Dugar logo");
            var info = document.getDocumentInformation();
            info.setTitle("REPAYMENT SCHEDULE - " + report.header().contractNumber());
            info.setAuthor(companyName);
            int index = 0;
            boolean totalsWritten = false;
            // Totals come from the shared unrounded SQL schedule, then format once.
            BigDecimal totalEmi = report.totals().emi();
            BigDecimal totalInterest = report.totals().interest();
            BigDecimal totalPrincipal = report.totals().principal();
            while (index < report.rows().size() || !totalsWritten) {
                var page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                document.addPage(page);
                try (var canvas = new PDPageContentStream(document, page)) {
                    float y = header(canvas, page, font, logo, document.getNumberOfPages() == 1);
                    if (document.getNumberOfPages() == 1) y = details(canvas, font, y, report) - 12;
                    tableHeader(canvas, font, y);
                    y -= 22;
                    while (index < report.rows().size()
                            && y - ROW_HEIGHT - (index == report.rows().size() - 1 ? 19 : 0) >= BOTTOM) {
                        Row row = report.rows().get(index);
                        cells(canvas, font, y, ROW_HEIGHT, new String[]{String.valueOf(row.installmentNo()),
                            date(row.dueDate()), money(row.openingPrincipal()), money(row.emiAmount()),
                            money(row.interestAmount()), money(row.principalAmount()), money(row.closingPrincipal())},
                            index % 2 == 0 ? Color.WHITE : new Color(242, 246, 250), Color.BLACK);
                        index++;
                        y -= ROW_HEIGHT;
                    }
                    if (index == report.rows().size() && y - 19 >= BOTTOM) {
                        var last = report.rows().get(report.rows().size() - 1);
                        cells(canvas, font, y, 19, new String[]{"TOTAL", "", "", money(totalEmi), money(totalInterest),
                            money(totalPrincipal), money(last.closingPrincipal())}, new Color(221, 232, 243), BLUE);
                        totalsWritten = true;
                    }
                }
            }
            for (int number = 0; number < document.getNumberOfPages(); number++) {
                var page = document.getPage(number);
                try (var canvas = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                    String label = "Page " + (number + 1) + " of " + document.getNumberOfPages();
                    text(canvas, font, 7, page.getMediaBox().getWidth() - MARGIN - width(font, label, 7), 42, label, MUTED);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private float header(PDPageContentStream canvas, PDPage page, PDType0Font font, PDImageXObject logo,
                         boolean firstPage) throws IOException {
        float top = page.getMediaBox().getHeight() - TOP;
        float logoWidth = firstPage ? 126 : 100;
        float logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
        float maxHeight = firstPage ? 36 : 28;
        if (logoHeight > maxHeight) {
            logoWidth *= maxHeight / logoHeight;
            logoHeight = maxHeight;
        }
        canvas.drawImage(logo, MARGIN + CONTENT_WIDTH - logoWidth, top - logoHeight, logoWidth, logoHeight);
        emphasis(canvas, font, firstPage ? 17 : 12, MARGIN, top - (firstPage ? 23 : 19), "REPAYMENT SCHEDULE", BLUE);
        float divider = top - Math.max(firstPage ? 36 : 28, logoHeight) - 8;
        canvas.setStrokingColor(new Color(171, 190, 209)); canvas.setLineWidth(0.6f);
        canvas.moveTo(MARGIN, divider); canvas.lineTo(MARGIN + CONTENT_WIDTH, divider); canvas.stroke();
        return divider - 12;
    }

    private float details(PDPageContentStream canvas, PDType0Font font, float top,
                          RepaymentScheduleData report) throws IOException {
        var h = report.header();
        String[][] metadata = {
            {"CONTRACT NUMBER", h.contractNumber(), "BORROWER NAME", h.borrowerName(), "CONTRACT DATE", date(h.contractDate())},
            {"LOAN AMOUNT", money(h.loanAmount()), "ANNUAL IRR", h.irrRate().toPlainString() + "%", "TENURE", h.tenureMonths() + " months"},
            {"FIRST EMI DATE", date(h.firstEmiDate()), "REPAYMENT FREQUENCY", "Monthly", "TOTAL INSTALLMENTS", String.valueOf(report.rows().size())}
        };
        List<String> structures = new ArrayList<>();
        long start = 1;
        for (Slab slab : report.slabs()) {
            long end = start + slab.installments() - 1;
            structures.add(report.slabs().size() == 1 ? slab.installments() + " × " + money(slab.amount())
                : start + (start == end ? "" : "–" + end) + ": " + money(slab.amount()));
            start = end + 1;
        }
        String structure = structures.isEmpty() ? "-" : String.join(" | ", structures);
        float columnWidth = (CONTENT_WIDTH - 24) / 3;
        // Measure wrapped content before drawing; reserve room for the first table row and totals.
        float scale = 1, panelHeight, stripHeight;
        float[] heights = new float[metadata.length];
        List<String> structureLines;
        do {
            panelHeight = 24;
            for (int row = 0; row < metadata.length; row++) {
                int lines = 1;
                for (int col = 0; col < 3; col++) {
                    lines = Math.max(lines, wrap(font, value(metadata[row][col * 2 + 1]), 9 * scale, columnWidth - 12).size());
                }
                heights[row] = (19 + lines * 11) * scale;
                panelHeight += heights[row];
            }
            structureLines = wrap(font, structure, 9 * scale, CONTENT_WIDTH - 48);
            stripHeight = (26 + structureLines.size() * 12) * scale;
            panelHeight += stripHeight;
            if (top - panelHeight - 12 - 22 - ROW_HEIGHT - 19 >= BOTTOM) break;
            scale *= 0.9f;
        } while (true);
        canvas.setNonStrokingColor(PANEL);
        canvas.setStrokingColor(BORDER); canvas.setLineWidth(0.6f);
        roundedPanel(canvas, MARGIN, top - panelHeight, CONTENT_WIDTH, panelHeight);
        canvas.fillAndStroke();
        float y = top - 12;
        for (int row = 0; row < metadata.length; row++) {
            for (int col = 0; col < 3; col++) {
                float x = MARGIN + 12 + col * columnWidth;
                text(canvas, font, 6.5f * scale, x, y - 7 * scale, metadata[row][col * 2], MUTED);
                var lines = wrap(font, value(metadata[row][col * 2 + 1]), 9 * scale, columnWidth - 12);
                for (int n = 0; n < lines.size(); n++) {
                    emphasis(canvas, font, 9 * scale, x, y - (20 + n * 11) * scale, lines.get(n), BLUE);
                }
            }
            y -= heights[row];
        }
        canvas.setNonStrokingColor(STRIP);
        canvas.addRect(MARGIN + 12, y - stripHeight, CONTENT_WIDTH - 24, stripHeight); canvas.fill();
        emphasis(canvas, font, 6.5f * scale, MARGIN + 24, y - 12 * scale, "EMI STRUCTURE", BLUE);
        for (int n = 0; n < structureLines.size(); n++) {
            emphasis(canvas, font, 9 * scale, MARGIN + 24, y - (26 + n * 12) * scale, structureLines.get(n), BLUE);
        }
        return top - panelHeight;
    }

    private static void roundedPanel(PDPageContentStream canvas, float x, float y, float width, float height) throws IOException {
        float r = 4, c = r * 0.55228475f;
        canvas.moveTo(x + r, y); canvas.lineTo(x + width - r, y);
        canvas.curveTo(x + width - r + c, y, x + width, y + r - c, x + width, y + r);
        canvas.lineTo(x + width, y + height - r);
        canvas.curveTo(x + width, y + height - r + c, x + width - r + c, y + height, x + width - r, y + height);
        canvas.lineTo(x + r, y + height);
        canvas.curveTo(x + r - c, y + height, x, y + height - r + c, x, y + height - r);
        canvas.lineTo(x, y + r); canvas.curveTo(x, y + r - c, x + r - c, y, x + r, y); canvas.closePath();
    }

    private void tableHeader(PDPageContentStream canvas, PDType0Font font, float y) throws IOException {
        cells(canvas, font, y, 22, new String[]{"EMI No.", "Due Date", "Opening Principal", "EMI Amount", "Interest", "Principal", "Closing Principal"}, BLUE, Color.WHITE);
    }

    private void cells(PDPageContentStream canvas, PDType0Font font, float y, float height,
                       String[] values, Color background, Color foreground) throws IOException {
        float x = MARGIN;
        for (int col = 0; col < WIDTHS.length; col++) {
            canvas.setNonStrokingColor(background);
            canvas.addRect(x, y - height, WIDTHS[col], height); canvas.fill();
            canvas.setStrokingColor(BORDER); canvas.setLineWidth(0.35f);
            canvas.addRect(x, y - height, WIDTHS[col], height); canvas.stroke();
            float size = 8;
            while (width(font, values[col], size) > WIDTHS[col] - 12) size -= 0.25f;
            float left = col >= 2 ? x + WIDTHS[col] - 6 - width(font, values[col], size) : x + 6;
            if (background.equals(BLUE) || "TOTAL".equals(values[0])) {
                emphasis(canvas, font, size, left, y - height / 2 - 2.7f, values[col], foreground);
            } else {
                text(canvas, font, size, left, y - height / 2 - 2.7f, values[col], foreground);
            }
            x += WIDTHS[col];
        }
    }

    static void text(PDPageContentStream canvas, PDType0Font font, float size, float x, float y,
                             String value, Color color) throws IOException {
        canvas.beginText(); canvas.setFont(font, size); canvas.setNonStrokingColor(color);
        canvas.newLineAtOffset(x, y); canvas.showText(value(value)); canvas.endText();
    }
    // Use the existing embedded Unicode font for a modest semibold stroke, including rupee glyphs.
    static void emphasis(PDPageContentStream canvas, PDType0Font font, float size, float x, float y,
                                 String value, Color color) throws IOException {
        canvas.saveGraphicsState(); canvas.setStrokingColor(color); canvas.setLineWidth(size * 0.025f);
        canvas.setRenderingMode(RenderingMode.FILL_STROKE);
        text(canvas, font, size, x, y, value, color);
        canvas.restoreGraphicsState();
    }
    static float width(PDType0Font font, String value, float size) throws IOException {
        return font.getStringWidth(value(value)) / 1000 * size;
    }
    private static String value(String value) { return value == null || value.isBlank() ? "-" : value.replaceAll("[\\p{Cntrl}]", " "); }
    private static String date(LocalDate date) { return date == null ? "-" : date.format(DATE); }

    // HALF_UP matches PostgreSQL numeric round(value, 0), including negative ties.
    static String money(BigDecimal value) {
        BigDecimal rounded = value.setScale(0, RoundingMode.HALF_UP);
        String integer = rounded.abs().toPlainString();
        if (integer.length() > 3) {
            String prefix = integer.substring(0, integer.length() - 3);
            integer = prefix.replaceAll("\\B(?=(\\d{2})+(?!\\d))", ",") + "," + integer.substring(integer.length() - 3);
        }
        return (rounded.signum() < 0 ? "-" : "") + "\u20b9" + integer;
    }

    static List<String> wrap(PDType0Font font, String text, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int codePoint : text.codePoints().toArray()) {
            String ch = new String(Character.toChars(codePoint));
            if (!line.isEmpty() && width(font, line + ch, size) > maxWidth) {
                int space = line.lastIndexOf(" ");
                if (space > 0) {
                    lines.add(line.substring(0, space).stripTrailing());
                    line.delete(0, space + 1);
                } else {
                    lines.add(line.toString().stripTrailing()); line.setLength(0);
                }
            }
            if (!line.isEmpty() || !ch.isBlank()) line.append(ch);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
