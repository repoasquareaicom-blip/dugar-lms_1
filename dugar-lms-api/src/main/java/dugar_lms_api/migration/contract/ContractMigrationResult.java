package dugar_lms_api.migration.contract;

import java.util.ArrayList;
import java.util.List;

public class ContractMigrationResult {

    private static final int MAX_ERRORS = 100;

    private Long migrationRunId;
    private boolean success;
    private String fileName;
    private String sheetName;
    private int totalRows;
    private int inserted;
    private int duplicatesSkipped;
    private int failed;
    private int calculatedInterestValues;
    private int interestCalculationWarnings;
    private int calculatedBpfcAmounts;
    private int bpfcCalculationWarnings;
    private final List<ContractMigrationDuplicate> duplicates = new ArrayList<>();
    private final List<ContractMigrationError> errors = new ArrayList<>();

    public static ContractMigrationResult forFile(String fileName, String sheetName, int totalRows) {
        ContractMigrationResult result = new ContractMigrationResult();
        result.success = true;
        result.fileName = fileName;
        result.sheetName = sheetName;
        result.totalRows = totalRows;
        return result;
    }

    public static ContractMigrationResult failure(String fileName, String sheetName, String reason) {
        ContractMigrationResult result = forFile(fileName, sheetName, 0);
        result.success = false;
        result.addError(0, null, null, reason);
        return result;
    }

    public void incrementInserted() {
        inserted++;
    }

    public void incrementDuplicatesSkipped() {
        duplicatesSkipped++;
    }

    public void incrementFailed() {
        failed++;
    }

    public void incrementCalculatedInterestValues() {
        calculatedInterestValues++;
    }

    public void incrementInterestCalculationWarnings() {
        interestCalculationWarnings++;
    }

    public void incrementCalculatedBpfcAmounts() {
        calculatedBpfcAmounts++;
    }

    public void incrementBpfcCalculationWarnings() {
        bpfcCalculationWarnings++;
    }

    public void addError(int excelRow, String contractType, String contractNumber, String reason) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(new ContractMigrationError(excelRow, contractType, contractNumber, reason));
        }
    }

    public void addDuplicate(int excelRow, String contractType, String contractNumber, String reason) {
        duplicates.add(new ContractMigrationDuplicate(excelRow, contractType, contractNumber, reason));
    }

    public Long getMigrationRunId() {
        return migrationRunId;
    }

    public void setMigrationRunId(Long migrationRunId) {
        this.migrationRunId = migrationRunId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getInserted() {
        return inserted;
    }

    public void setInserted(int inserted) {
        this.inserted = inserted;
    }

    public int getDuplicatesSkipped() {
        return duplicatesSkipped;
    }

    public void setDuplicatesSkipped(int duplicatesSkipped) {
        this.duplicatesSkipped = duplicatesSkipped;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public int getCalculatedInterestValues() {
        return calculatedInterestValues;
    }

    public int getInterestCalculationWarnings() {
        return interestCalculationWarnings;
    }

    public int getCalculatedBpfcAmounts() {
        return calculatedBpfcAmounts;
    }

    public int getBpfcCalculationWarnings() {
        return bpfcCalculationWarnings;
    }

    public List<ContractMigrationDuplicate> getDuplicates() {
        return duplicates;
    }

    public List<ContractMigrationError> getErrors() {
        return errors;
    }
}
