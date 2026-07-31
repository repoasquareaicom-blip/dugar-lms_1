package dugar_lms_api.migration.repaymentstructure;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class RepaymentStructureExcelReader {

    private static final Set<String> REQUIRED_HEADERS = Set.of("CONT_TYPE", "CONT_NO", "SNO", "NO_OF_INST", "INST_AMT");

    public List<RepaymentStructureSourceRow> read(InputStream inputStream, String sheetName) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found: " + sheetName);
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headers = headers(sheet.getRow(sheet.getFirstRowNum()), formatter, evaluator);
            validateHeaders(headers);

            List<RepaymentStructureSourceRow> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                RepaymentStructureSourceRow sourceRow = row == null
                    ? blankRow(rowIndex + 1)
                    : new RepaymentStructureSourceRow(
                        rowIndex + 1,
                        text(row, headers, "CONT_TYPE", formatter, evaluator),
                        text(row, headers, "CONT_NO", formatter, evaluator),
                        text(row, headers, "SNO", formatter, evaluator),
                        text(row, headers, "NO_OF_INST", formatter, evaluator),
                        text(row, headers, "INST_AMT", formatter, evaluator)
                    );
                rows.add(sourceRow);
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read repayment structure source sheet: " + sheetName, exception);
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

    private void validateHeaders(Map<String, Integer> headers) {
        List<String> missing = REQUIRED_HEADERS.stream()
            .filter(header -> !headers.containsKey(header))
            .sorted()
            .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required repayment structure headers: " + String.join(", ", missing));
        }
    }

    private String text(Row row, Map<String, Integer> headers, String header, DataFormatter formatter, FormulaEvaluator evaluator) {
        Integer cellIndex = headers.get(header);
        String value = formatter.formatCellValue(row.getCell(cellIndex), evaluator);
        return value == null ? null : value.trim();
    }

    private RepaymentStructureSourceRow blankRow(int excelRow) {
        return new RepaymentStructureSourceRow(excelRow, null, null, null, null, null);
    }
}
