package dugar_lms_api.modules.reports.stockstatement;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;

@Component
public class StockStatementExcel {
    static final String[] HEADINGS = {"Contract No", "Contract Date", "HIRER NAME", "VEHICLE MAKE",
        "100% OF the amt", "Amount per instalment", "Total EMI", "Balance EMI", "Balance Dues amount"};

    public void write(StockStatementResponse report, OutputStream output) throws IOException {
        try (var book = new SXSSFWorkbook(100)) {
            book.setCompressTempFiles(true);
            var text = style(book, false, HorizontalAlignment.LEFT, null);
            var money = style(book, false, HorizontalAlignment.RIGHT, "#,##0.00");
            var integer = style(book, false, HorizontalAlignment.RIGHT, "0");
            var date = style(book, false, HorizontalAlignment.CENTER, "dd/mm/yyyy");
            var heading = style(book, true, HorizontalAlignment.CENTER, null);
            Sheet sheet = null;
            int number = 0;
            for (var record : report.rows()) {
                if (sheet == null || sheet.getLastRowNum() >= 1_048_575) {
                    sheet = sheet(book, report, ++number, heading);
                }
                var row = sheet.createRow(sheet.getLastRowNum() + 1);
                row.setHeightInPoints(Math.min(409, 15 * Math.max(lines(record.hirerName(), 38), lines(record.vehicleDetails(), 26)) + 8));
                Object[] values = {record.contractNumber(), record.contractDate(), record.hirerName(), record.vehicleDetails(),
                    record.loanAmount(), record.principalPerInstallment(), record.totalEmi(), record.balanceEmi(), record.principalOutstanding()};
                for (int col = 0; col < values.length; col++) {
                    Cell cell = row.createCell(col);
                    Object value = values[col];
                    cell.setCellStyle(col == 1 ? date : col == 6 || col == 7 ? integer : col >= 4 ? money : text);
                    if (value instanceof Number n) cell.setCellValue(n.doubleValue());
                    else if (value instanceof java.time.LocalDate d) cell.setCellValue(d);
                    else cell.setCellValue(value == null ? "" : value.toString());
                }
            }
            if (sheet == null) sheet = sheet(book, report, ++number, heading);
            book.write(output);
        }
    }

    private Sheet sheet(SXSSFWorkbook book, StockStatementResponse report, int number, CellStyle heading) {
        Sheet sheet = book.createSheet("Stock Statement" + (number == 1 ? "" : " " + number));
        String[] titles = {"DUGAR FINANCE & INVESTMENTS LIMITED",
            "STOCK STATEMENT AS ON " + report.businessDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), report.bankName()};
        for (int i = 0; i < titles.length; i++) {
            var row = sheet.createRow(i);
            row.setHeightInPoints(24);
            for (int col = 0; col < 9; col++) row.createCell(col).setCellStyle(heading);
            row.getCell(0).setCellValue(titles[i]);
            sheet.addMergedRegion(new CellRangeAddress(i, i, 0, 8));
        }
        Row headers = sheet.createRow(3);
        headers.setHeightInPoints(32);
        int[] widths = {20, 15, 42, 30, 20, 22, 12, 14, 23};
        for (int col = 0; col < 9; col++) {
            headers.createCell(col).setCellValue(HEADINGS[col]);
            headers.getCell(col).setCellStyle(heading);
            sheet.setColumnWidth(col, widths[col] * 256);
        }
        sheet.createFreezePane(2, 4);
        sheet.setRepeatingRows(new CellRangeAddress(0, 3, -1, -1));
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        return sheet;
    }

    private int lines(String value, int width) {
        if (value == null) return 1;
        return value.lines().mapToInt(line -> Math.max(1, (line.length() + width - 1) / width)).sum();
    }

    private CellStyle style(Workbook book, boolean bold, HorizontalAlignment align, String format) {
        var style = book.createCellStyle();
        var font = book.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 11);
        font.setBold(bold);
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        if (format != null) style.setDataFormat(book.createDataFormat().getFormat(format));
        return style;
    }
}
