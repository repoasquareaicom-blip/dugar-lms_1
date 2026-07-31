package dugar_lms_api.migration.contract;

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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class ContractExcelReader {

    private static final Set<String> REQUIRED_HEADERS = Set.of("CONT_TYPE", "CONT_NO");
    private static final Set<String> DATE_HEADERS = Set.of("CONT_DT", "EFF_DT", "CLOSE_DATE", "PDCDATE", "REGN_DATE");
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ISO_LOCAL_DATE
    );

    public ParsedSheet read(InputStream inputStream, String sheetName) {
        return read(inputStream, sheetName, null);
    }

    public ParsedSheet read(InputStream inputStream, String sheetName, String workbookName) {
        Objects.requireNonNull(inputStream, "Input stream must not be null");
        Objects.requireNonNull(sheetName, "Sheet name must not be null");

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                if (workbookName == null || workbookName.isBlank()) {
                    throw new IllegalArgumentException("Sheet '" + sheetName + "' not found");
                }
                throw new IllegalArgumentException("Worksheet '" + sheetName + "' not found in " + workbookName);
            }

            DataFormatter formatter = new DataFormatter(Locale.ENGLISH, true);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            int headerRowIndex = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(headerRowIndex);
            if (headerRow == null) {
                throw new IllegalArgumentException("Header row is missing in sheet '" + sheetName + "'");
            }

            Map<String, Integer> headers = readHeaders(headerRow, formatter, evaluator);
            validateRequiredHeaders(headers.keySet());

            List<LegacyRow> rows = new ArrayList<>();
            for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyRow(row, formatter, evaluator)) {
                    continue;
                }

                Map<String, Object> values = new HashMap<>();
                for (Map.Entry<String, Integer> entry : headers.entrySet()) {
                    Cell cell = row.getCell(entry.getValue(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values.put(entry.getKey(), extractCellValue(entry.getKey(), cell, formatter, evaluator));
                }

                rows.add(new LegacyRow(rowIndex + 1, values));
            }

            return new ParsedSheet(sheetName, rows, headers.keySet());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read Excel workbook", exception);
        }
    }

    private Map<String, Integer> readHeaders(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> headers = new LinkedHashMap<>();

        short lastCell = headerRow.getLastCellNum();
        if (lastCell <= 0) {
            throw new IllegalArgumentException("Header row is empty");
        }

        for (int i = 0; i < lastCell; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String header = normalize(formatter.formatCellValue(cell, evaluator)).toUpperCase(Locale.ROOT);
            if (!header.isBlank()) {
                headers.put(header, i);
            }
        }

        if (headers.isEmpty()) {
            throw new IllegalArgumentException("No headers found in first row");
        }

        return headers;
    }

    private void validateRequiredHeaders(Set<String> headers) {
        for (String required : REQUIRED_HEADERS) {
            if (!headers.contains(required)) {
                throw new IllegalArgumentException("Required header missing: " + required);
            }
        }
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        short firstCellNum = row.getFirstCellNum();
        short lastCellNum = row.getLastCellNum();

        if (firstCellNum < 0 || lastCellNum < 0) {
            return true;
        }

        for (int i = firstCellNum; i < lastCellNum; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                continue;
            }

            String formatted = normalize(formatter.formatCellValue(cell, evaluator));
            if (!formatted.isBlank()) {
                return false;
            }
        }

        return true;
    }

    private Object extractCellValue(String headerName, Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }

        if ("EQUP_MODEL".equals(headerName)) {
            String value = normalize(formatter.formatCellValue(cell, evaluator));
            return value.isBlank() ? null : value;
        }

        if (DATE_HEADERS.contains(headerName)) {
            return getLocalDate(cell, formatter, evaluator);
        }

        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = evaluator.evaluateFormulaCell(cell);
        }

        return switch (cellType) {
            case BLANK -> null;
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield getLocalDate(cell, formatter, evaluator);
                }
                String numberText = NumberToTextConverter.toText(cell.getNumericCellValue());
                yield new BigDecimal(numberText);
            }
            case STRING -> {
                String value = normalize(cell.getStringCellValue());
                yield value.isBlank() ? null : value;
            }
            default -> {
                String value = normalize(cell.toString());
                yield value.isBlank() ? null : value;
            }
        };
    }

    private LocalDate getLocalDate(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }

        String formatted = normalize(formatter.formatCellValue(cell, evaluator));
        if (formatted.isBlank()) {
            return null;
        }

        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            return parseTextDate(formatted);
        }

        if (cellType == CellType.NUMERIC) {
            if (!DateUtil.isCellDateFormatted(cell) || !DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
                return null;
            }

            LocalDateTime localDateTime = cell.getLocalDateTimeCellValue();
            return localDateTime == null ? null : localDateTime.toLocalDate();
        }

        if (cellType == CellType.STRING) {
            return parseTextDate(formatted);
        }

        return null;
    }

    private LocalDate parseTextDate(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record ParsedSheet(
        String sheetName,
        List<LegacyRow> rows,
        Set<String> headers
    ) {
    }

    public record LegacyRow(
        int excelRow,
        Map<String, Object> values
    ) {
        public Object value(String headerName) {
            if (headerName == null) {
                return null;
            }
            return values.get(headerName.toUpperCase(Locale.ROOT));
        }

        public boolean hasHeader(String headerName) {
            return headerName != null && values.containsKey(headerName.toUpperCase(Locale.ROOT));
        }
    }
}
