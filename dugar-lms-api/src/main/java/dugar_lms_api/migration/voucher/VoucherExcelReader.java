package dugar_lms_api.migration.voucher;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class VoucherExcelReader {

    public List<VoucherSourceRow> read(InputStream inputStream, String sheetName, Set<String> requiredHeaders) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Worksheet '" + sheetName + "' not found");
            }

            DataFormatter formatter = new DataFormatter(Locale.ENGLISH, true);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headers = headers(sheet.getRow(sheet.getFirstRowNum()), formatter, evaluator);
            validateHeaders(headers, requiredHeaders);

            List<VoucherSourceRow> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                Map<String, Object> values = new LinkedHashMap<>();
                for (String header : headers.keySet()) {
                    Cell cell = row == null ? null : row.getCell(headers.get(header), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values.put(header, value(cell, formatter, evaluator));
                }
                rows.add(new VoucherSourceRow(rowIndex + 1, values));
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read voucher source sheet: " + sheetName, exception);
        }
    }

    private Map<String, Integer> headers(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (headerRow == null) {
            throw new IllegalArgumentException("Header row is missing");
        }

        Map<String, Integer> headers = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell, evaluator).trim().toUpperCase(Locale.ROOT);
            if (!header.isBlank()) {
                headers.put(header, cell.getColumnIndex());
            }
        }
        return headers;
    }

    private void validateHeaders(Map<String, Integer> headers, Set<String> requiredHeaders) {
        List<String> missing = requiredHeaders.stream()
            .filter(header -> !headers.containsKey(header))
            .sorted()
            .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required voucher headers: " + String.join(", ", missing));
        }
    }

    private Object value(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = evaluator.evaluateFormulaCell(cell);
        }

        return switch (type) {
            case BLANK -> null;
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell) && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
                    LocalDateTime localDateTime = cell.getLocalDateTimeCellValue();
                    yield localDateTime == null ? null : localDateTime.toLocalDate();
                }
                yield new BigDecimal(NumberToTextConverter.toText(cell.getNumericCellValue()));
            }
            case STRING -> {
                String text = cell.getStringCellValue() == null ? null : cell.getStringCellValue().trim();
                yield text == null || text.isBlank() ? null : text;
            }
            default -> {
                String text = formatter.formatCellValue(cell, evaluator).trim();
                yield text.isBlank() ? null : text;
            }
        };
    }
}
