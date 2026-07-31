package dugar_lms_api.migration.borrower;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class BorrowerExcelReader {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
        "PARTY_CODE",
        "PARTY_NAME1",
        "PARTY_TYPE"
    );

    public ParsedBorrowerSheet read(InputStream inputStream, String sheetName) {
        Objects.requireNonNull(inputStream, "Input stream must not be null");
        Objects.requireNonNull(sheetName, "Sheet name must not be null");

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet '" + sheetName + "' not found");
            }

            DataFormatter formatter = new DataFormatter(Locale.ENGLISH, true);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("Header row is missing in sheet '" + sheetName + "'");
            }

            Map<String, Integer> headers = readHeaders(headerRow, formatter, evaluator);
            validateRequiredHeaders(headers.keySet());

            List<BorrowerMigrationRow> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    rows.add(blankRow(rowIndex + 1));
                    continue;
                }

                rows.add(new BorrowerMigrationRow(
                    rowIndex + 1,
                    value(row, headers, "PARTY_CODE", formatter, evaluator),
                    value(row, headers, "PARTY_PREFIX", formatter, evaluator),
                    value(row, headers, "PARTY_NAME1", formatter, evaluator),
                    value(row, headers, "PARTY_NAME2", formatter, evaluator),
                    value(row, headers, "PARTY_ADD1", formatter, evaluator),
                    value(row, headers, "PARTY_ADD2", formatter, evaluator),
                    value(row, headers, "PARTY_ADD3", formatter, evaluator),
                    value(row, headers, "PARTY_CITY", formatter, evaluator),
                    value(row, headers, "PARTY_STATE", formatter, evaluator),
                    value(row, headers, "PARTY_PIN", formatter, evaluator),
                    value(row, headers, "PARTY_PNO", formatter, evaluator),
                    value(row, headers, "PARTY_TYPE", formatter, evaluator)
                ));
            }

            return new ParsedBorrowerSheet(sheetName, rows, new LinkedHashSet<>(headers.keySet()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read Excel workbook", exception);
        }
    }

    private BorrowerMigrationRow blankRow(int excelRow) {
        return new BorrowerMigrationRow(excelRow, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private Map<String, Integer> readHeaders(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        short lastCell = headerRow.getLastCellNum();
        for (int i = 0; i < lastCell; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String header = normalize(formatter.formatCellValue(cell, evaluator)).toUpperCase(Locale.ROOT);
            if (!header.isBlank()) {
                headers.put(header, i);
            }
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

    private String value(
        Row row,
        Map<String, Integer> headers,
        String headerName,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        Integer index = headers.get(headerName);
        if (index == null) {
            return null;
        }

        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cellValue(cell, formatter, evaluator);
    }

    private String cellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }

        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = evaluator.evaluateFormulaCell(cell);
        }

        String value = switch (cellType) {
            case BLANK -> null;
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC -> NumberToTextConverter.toText(cell.getNumericCellValue());
            case STRING -> cell.getStringCellValue();
            default -> formatter.formatCellValue(cell, evaluator);
        };

        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record ParsedBorrowerSheet(
        String sheetName,
        List<BorrowerMigrationRow> rows,
        Set<String> headers
    ) {
    }
}
