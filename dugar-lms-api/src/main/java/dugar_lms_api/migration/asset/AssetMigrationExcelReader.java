package dugar_lms_api.migration.asset;

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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AssetMigrationExcelReader {

    public List<AssetMigrationRow> readDetails(InputStream inputStream, String sheetName) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = requiredSheet(workbook, sheetName);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headers = headers(sheet.getRow(sheet.getFirstRowNum()), formatter, evaluator);
            List<AssetMigrationRow> rows = new ArrayList<>();

            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    rows.add(new AssetMigrationRow(rowIndex + 1, null, null, null, null, null, null, null, null));
                    continue;
                }
                rows.add(new AssetMigrationRow(
                    rowIndex + 1,
                    text(row, headers, "CONT_TYPE", formatter, evaluator),
                    text(row, headers, "CONT_NO", formatter, evaluator),
                    text(row, headers, "TYPE_OF_VEHICLE", formatter, evaluator),
                    decimal(row, headers, "EQUP_VALUE", formatter, evaluator),
                    text(row, headers, "SECURITY_OFFERED", formatter, evaluator),
                    text(row, headers, "ENGINE_NO", formatter, evaluator),
                    text(row, headers, "CHASIS_NO", formatter, evaluator),
                    text(row, headers, "NO_OF_OWNERSHIP", formatter, evaluator)
                ));
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read asset source sheet: " + sheetName, exception);
        }
    }

    public List<ContractAssetSupplement> readContractSupplements(InputStream inputStream, String sheetName) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = requiredSheet(workbook, sheetName);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headers = headers(sheet.getRow(sheet.getFirstRowNum()), formatter, evaluator);
            List<ContractAssetSupplement> rows = new ArrayList<>();

            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                rows.add(new ContractAssetSupplement(
                    text(row, headers, "CONT_TYPE", formatter, evaluator),
                    text(row, headers, "CONT_NO", formatter, evaluator),
                    text(row, headers, "REGIS_NO", formatter, evaluator),
                    text(row, headers, "EQUP_MODEL", formatter, evaluator)
                ));
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read contract source sheet: " + sheetName, exception);
        }
    }

    private Sheet requiredSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet not found: " + sheetName);
        }
        return sheet;
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

    private BigDecimal decimal(Row row, Map<String, Integer> headers, String header, DataFormatter formatter, FormulaEvaluator evaluator) {
        String value = text(row, headers, header, formatter, evaluator);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid numeric value for " + header + ": " + value);
        }
    }
}
