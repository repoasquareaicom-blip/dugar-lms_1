package dugar_lms_api.modules.reports.repaymentschedule;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dugar_lms_api.modules.reports.repaymentschedule.RepaymentSchedulePdf.*;

@Component
public class NormalRepaymentSchedulePdf {
    private static final float MARGIN = 72, WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
    private static final float TOP = PDRectangle.A4.getHeight() - 54, BOTTOM = 55;
    private static final float PAGE_FOOTER_Y = 36;
    private static final float GAP = 8, ROW = 13, TABLE_HEADER = 17, LEADING = 13;
    private static final Color BLUE = new Color(23, 54, 93), INK = new Color(26, 37, 50);
    private static final Color MUTED = new Color(74, 91, 110), BORDER = new Color(188, 202, 217);
    private static final Color TINT = new Color(243, 247, 251), TOTAL = new Color(222, 234, 246);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-uuuu");
    private static final String DISCLAIMER = "This is a computer-generated document and does not require a signature.";
    private static final String DELAY_TERMS = "In case the instalments are not paid on or before the due dates, "
        + "compensation for delayed payments @3.0% per month, on monthly resets will be levied on the instalment "
        + "amount. In case of cheque/ECS return, charges of Rs.500/- per cheque/ECS return will be debited to your account.";

    private final String companyName;
    private final String office;
    private final String phones;

    public NormalRepaymentSchedulePdf(
        @Value("${app.company-name:DUGAR FINANCE & INVESTMENTS LIMITED}") String companyName,
        @Value("${app.company-registered-office:Duggar  chambers, No.9, Damodaran Street, Chetpet,\nChennai, Tamil Nadu - 600031.}") String office,
        @Value("${app.company-phones: 044 28586464 / +91 93830 07676}") String phones
    ) {
        this.companyName = companyName;
        this.office = office;
        this.phones = phones;
    }

    public byte[] generate(NormalRepaymentSchedule report) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream();
             var fontInput = new ClassPathResource("reports/fonts/NotoSans-Regular.ttf").getInputStream();
             var logoInput = new ClassPathResource("reports/images/dugar-logo.png").getInputStream()) {
            var font = PDType0Font.load(document, fontInput, true);
            var logo = PDImageXObject.createFromByteArray(document, logoInput.readAllBytes(), "Dugar logo");
            document.getDocumentInformation().setTitle("REPAYMENT SCHEDULE - " + report.details().contractNumber());
            document.getDocumentInformation().setAuthor(companyName);
            var totalLines = wrap(font, "The total amount payable by you on the above vehicle is Rs. "
                + money(report.total()).substring(1) + "/- and we shall be happy if you remit the entire instalments "
                + "promptly on or before the due dates and be eligible for rebate as per the enclosed.", 9, WIDTH);
            var termsLines = wrap(font, DELAY_TERMS, 9, WIDTH);
            float footerHeight = 116 + (totalLines.size() + termsLines.size()) * LEADING;
            int index = 0;
            boolean footerWritten = false;
            while (!footerWritten) {
                var page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                boolean first = document.getNumberOfPages() == 1;
                try (var canvas = new PDPageContentStream(document, page)) {
                    float y = first ? firstPage(canvas, font, logo, report)
                        : continuation(canvas, font, report.details().contractNumber());
                    int remaining = report.installments().size() - index;
                    int capacityWithFooter = 3 * tableRows(y - footerHeight);
                    int capacity = 3 * tableRows(y);
                    int count = remaining <= capacityWithFooter ? remaining : Math.min(Math.max(0, remaining - 12), capacity);
                    if (count > 0) {
                        var rows = report.installments().subList(index, index + count);
                        y = tables(canvas, font, y, rows, tableRows(y));
                        index += count;
                    }
                    if (index == report.installments().size()) {
                        footer(canvas, font, y - 16, report, totalLines, termsLines);
                        float disclaimerX = MARGIN + (WIDTH - width(font, DISCLAIMER, 8)) / 2;
                        emphasis(canvas, font, 8, disclaimerX, PAGE_FOOTER_Y, DISCLAIMER, Color.DARK_GRAY);
                        footerWritten = true;
                    }
                    right(canvas, font, 8, MARGIN + WIDTH, PAGE_FOOTER_Y, "Page " + document.getNumberOfPages(), MUTED);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private float firstPage(PDPageContentStream canvas, PDType0Font font, PDImageXObject logo,
                            NormalRepaymentSchedule report) throws IOException {
        var details = report.details();
        String contractDate = date(details.contractDate());
        float scale = Math.min(90f / logo.getWidth(), 36f / logo.getHeight());
        float logoWidth = logo.getWidth() * scale, logoHeight = logo.getHeight() * scale;
        canvas.drawImage(logo, MARGIN + WIDTH - logoWidth, TOP - logoHeight, logoWidth, logoHeight);
        float y = TOP - 12;
        for (String line : wrap(font, companyName, 13, WIDTH - 102)) {
            emphasis(canvas, font, 13, MARGIN, y, line, BLUE);
            y -= 17;
        }
        String officeAddress = "Administrative office: "
            + office.replaceFirst("(?i),\\h+(?=Chennai,\\h+Tamil Nadu)", ",\n");
        for (String addressLine : officeAddress.lines().toList()) {
            y = lines(canvas, font, MARGIN, y, 8, wrap(font, addressLine.strip(), 8, WIDTH - 102), MUTED);
        }
        text(canvas, font, 8, MARGIN, y, "Phone: " + phones, MUTED);
        y -= 31;
        emphasis(canvas, font, 16, MARGIN, y, "REPAYMENT SCHEDULE", BLUE);
        right(canvas, font, 9, MARGIN + WIDTH, y, "Date: " + contractDate, MUTED);
        rule(canvas, y - 8);

        y -= 22;
        float leftWidth = WIDTH * 0.54f;
        float borrowerY = y - 11;
        if (present(details.borrowerName())) {
            for (String line : wrap(font, clean(details.borrowerName()), 10, leftWidth - 12)) {
                emphasis(canvas, font, 10, MARGIN, borrowerY, line, INK);
                borrowerY -= LEADING;
            }
        }
        String related = present(details.relatedPerson()) ? "S/W/D: " + clean(details.relatedPerson()) : "";
        String locality = join(", ", details.city(), details.state(), details.pinCode());
        for (String addressLine : List.of(related, clean(details.addressLine1()), clean(details.addressLine2()),
                                        clean(details.area()), locality)) {
            borrowerY = lines(canvas, font, MARGIN, borrowerY, 9, wrap(font, addressLine, 9, leftWidth - 12), INK);
        }
        String[][] fields = {
            {"Contract Date", contractDate},
            {"Contract Number", details.contractNumber()},
            {"Tenure", details.tenureMonths() == null ? "-" : details.tenureMonths() + " months"},
            {"Repayment Frequency", report.frequency()},
            {"First EMI Date", date(details.firstEmiDate())}
        };
        float contractY = summary(canvas, font, MARGIN + leftWidth, y, WIDTH - leftWidth, fields, false);
        y = Math.min(borrowerY, contractY) - 16;
        text(canvas, font, 9, MARGIN, y, "Dear Sir,", INK);
        y -= 18;
        String subject = "REG: H.P. A/C NO. " + clean(details.contractNumber())
            + (present(details.vehicle()) ? " - " + clean(details.vehicle()) : "") + " - REPAYMENT SCHEDULE";
        for (String line : wrap(font, subject, 9, WIDTH)) {
            emphasis(canvas, font, 9, MARGIN, y, line, INK);
            y -= LEADING;
        }
        return lines(canvas, font, MARGIN, y - 3, 9, wrap(font,
            "With reference to the above vehicle held under HP/Leasing Agreement entered with us, we furnish "
                + "below the amount payable in instalments on the following due dates:", 9, WIDTH), INK) - 6;
    }

    private float continuation(PDPageContentStream canvas, PDType0Font font, String contractNumber) throws IOException {
        emphasis(canvas, font, 13, MARGIN, TOP, "REPAYMENT SCHEDULE", BLUE);
        right(canvas, font, 9, MARGIN + WIDTH, TOP, clean(contractNumber), MUTED);
        rule(canvas, TOP - 10);
        return TOP - 25;
    }

    private static int tableRows(float top) {
        return Math.max(0, (int) ((top - BOTTOM - TABLE_HEADER) / ROW));
    }

    static int rowsPerTable(int count, int maxRows) {
        int preferred = count <= 12 ? 1 : count <= 24 ? 2 : 3;
        int columns = Math.max(preferred, (count + maxRows - 1) / maxRows);
        return (count + columns - 1) / columns;
    }

    private float tables(PDPageContentStream canvas, PDType0Font font, float top,
                         List<NormalRepaymentSchedule.Installment> rows, int maxRows) throws IOException {
        int perTable = rowsPerTable(rows.size(), maxRows);
        float tableWidth = (WIDTH - 2 * GAP) / 3;
        for (int start = 0; start < rows.size(); start += perTable) {
            float x = MARGIN + start / perTable * (tableWidth + GAP);
            cells(canvas, font, x, top, tableWidth, TABLE_HEADER, new String[]{"S.No", "Due Date", "Due Amount"}, BLUE, Color.WHITE);
            float y = top - TABLE_HEADER;
            for (int index = start; index < Math.min(start + perTable, rows.size()); index++) {
                var row = rows.get(index);
                cells(canvas, font, x, y, tableWidth, ROW, new String[]{
                    Integer.toString(row.number()), date(row.dueDate()), money(row.amount())
                }, index % 2 == 0 ? Color.WHITE : TINT, INK);
                y -= ROW;
            }
        }
        return top - TABLE_HEADER - perTable * ROW;
    }

    private void cells(PDPageContentStream canvas, PDType0Font font, float x, float top, float width, float height,
                       String[] values, Color background, Color foreground) throws IOException {
        float[] widths = {width * 0.17f, width * 0.39f, width * 0.44f};
        for (int i = 0; i < values.length; i++) {
            box(canvas, x, top, widths[i], height, background);
            float size = 8;
            while (width(font, values[i], size) > widths[i] - 8 && size > 6) size -= 0.25f;
            float baseline = top - height / 2 - 3;
            if (i == 2) right(canvas, font, size, x + widths[i] - 4, baseline, values[i], foreground);
            else text(canvas, font, size, x + (widths[i] - width(font, values[i], size)) / 2, baseline, values[i], foreground);
            x += widths[i];
        }
    }

    private void footer(PDPageContentStream canvas, PDType0Font font, float y, NormalRepaymentSchedule report,
                        List<String> totalLines, List<String> termsLines) throws IOException {
        y = lines(canvas, font, MARGIN, y, 9, totalLines, INK) - 5;
        y = lines(canvas, font, MARGIN, y, 9, termsLines, INK) - 9;
        float rightWidth = WIDTH * 0.42f;
        var details = report.details();
        // The contractual total comes from the repayment slabs. Deposits and BPFC are separate stored values.
        summary(canvas, font, MARGIN + WIDTH - rightWidth, y + 7, rightWidth, new String[][]{
            {"Finance Amount", amount(details.financeAmount())},
            {"Finance Charges", amount(details.financeCharges())},
            {"BPFC", amount(details.bpfc())},
            {"Insurance Deposit", amount(details.insuranceDeposit())},
            {"TOTAL", money(report.total())}
        }, true);
        text(canvas, font, 9, MARGIN, y, "Thanking You,", INK);

    }

    private float summary(PDPageContentStream canvas, PDType0Font font, float x, float top, float width,
                          String[][] fields, boolean highlightTotal) throws IOException {
        for (int index = 0; index < fields.length; index++) {
            String label = fields[index][0], value = fields[index][1];
            float valueWidth = width * 0.53f;
            List<String> wrapped = wrap(font, present(value) ? clean(value) : "-", 8.5f, valueWidth - 10);
            float height = Math.max(16, wrapped.size() * 11 + 5);
            boolean total = highlightTotal && index == fields.length - 1;
            box(canvas, x, top, width, height, total ? TOTAL : TINT);
            if (total) emphasis(canvas, font, 8.5f, x + 7, top - 11, label, BLUE);
            else text(canvas, font, 8.5f, x + 7, top - 11, label, MUTED);
            float y = top - 11;
            for (String line : wrapped) {
                right(canvas, font, 8.5f, x + width - 7, y, line, total ? BLUE : INK);
                y -= 11;
            }
            top -= height;
        }
        return top;
    }

    private static float lines(PDPageContentStream canvas, PDType0Font font, float x, float y,
                               float size, List<String> lines, Color color) throws IOException {
        for (String line : lines) {
            text(canvas, font, size, x, y, line, color);
            y -= LEADING;
        }
        return y;
    }

    private static void box(PDPageContentStream canvas, float x, float top, float width, float height, Color fill) throws IOException {
        canvas.setNonStrokingColor(fill);
        canvas.setStrokingColor(BORDER);
        canvas.setLineWidth(0.35f);
        canvas.addRect(x, top - height, width, height);
        canvas.fillAndStroke();
    }

    private static void rule(PDPageContentStream canvas, float y) throws IOException {
        canvas.setStrokingColor(BORDER);
        canvas.setLineWidth(0.55f);
        canvas.moveTo(MARGIN, y);
        canvas.lineTo(MARGIN + WIDTH, y);
        canvas.stroke();
    }

    private static void right(PDPageContentStream canvas, PDType0Font font, float size,
                              float right, float y, String value, Color color) throws IOException {
        text(canvas, font, size, right - width(font, value, size), y, value, color);
    }

    private static String date(LocalDate date) { return date == null ? "-" : date.format(DATE); }
    private static String amount(BigDecimal amount) {
        if (amount == null) return "-";
        BigDecimal absolute = amount.abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal rupees = absolute.setScale(0, RoundingMode.DOWN);
        BigDecimal paise = absolute.subtract(rupees);
        return (amount.signum() < 0 ? "-" : "") + money(rupees)
            + (paise.signum() == 0 ? "" : paise.toPlainString().substring(1));
    }
    private static boolean present(String value) { return !clean(value).isEmpty(); }

    private static String clean(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("^[ ,]+|[ ,]+$", "").strip();
        return cleaned.equalsIgnoreCase("null") || cleaned.equalsIgnoreCase("undefined") ? "" : cleaned;
    }

    private static String join(String delimiter, String... values) {
        return Stream.of(values).map(NormalRepaymentSchedulePdf::clean)
            .filter(value -> !value.isEmpty()).collect(Collectors.joining(delimiter));
    }
}
