package dugar_lms_api.migration.assetinsurance;

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

@Component
public class AssetInsuranceExcelReader {

    public List<AssetInsuranceSourceRow> read(InputStream inputStream, String sheetName) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found: " + sheetName);
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headers = headers(sheet.getRow(sheet.getFirstRowNum()), formatter, evaluator);
            List<AssetInsuranceSourceRow> rows = new ArrayList<>();

            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    rows.add(blankRow(rowIndex + 1));
                    continue;
                }
                rows.add(new AssetInsuranceSourceRow(
                    rowIndex + 1,
                    text(row, headers, "CONT_TYPE", formatter, evaluator),
                    text(row, headers, "CONT_NO", formatter, evaluator),
                    text(row, headers, "INS_CODE", formatter, evaluator),
                    text(row, headers, "POLICY_NO", formatter, evaluator),
                    text(row, headers, "CN_NO", formatter, evaluator),
                    text(row, headers, "POLICY_DT", formatter, evaluator),
                    text(row, headers, "VALID_FROM", formatter, evaluator),
                    text(row, headers, "VALID_TO", formatter, evaluator),
                    text(row, headers, "POLICY_BY", formatter, evaluator),
                    text(row, headers, "POLICY_AMT", formatter, evaluator)
                ));
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read asset insurance source sheet: " + sheetName, exception);
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

    private String text(Row row, Map<String, Integer> headers, String header, DataFormatter formatter, FormulaEvaluator evaluator) {
        Integer cellIndex = headers.get(header);
        if (cellIndex == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(cellIndex), evaluator);
        return value == null ? null : value.trim();
    }

    private AssetInsuranceSourceRow blankRow(int excelRow) {
        return new AssetInsuranceSourceRow(excelRow, null, null, null, null, null, null, null, null, null, null);
    }
}
