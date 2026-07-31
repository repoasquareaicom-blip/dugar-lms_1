package dugar_lms_api.migration.assetinsurance;

import java.util.ArrayList;
import java.util.List;

public class AssetInsuranceMigrationSummary {

    private static final int MAX_ISSUES = 100;

    private boolean success = true;
    private boolean completedWithWarnings;
    private int totalSourceRows;
    private int eligibleRows;
    private int inserted;
    private int duplicates;
    private int conflicts;
    private int missingContracts;
    private int missingAssets;
    private int ambiguousAssets;
    private int failed;
    private int skippedBlankRows;
    private int repeatedHeaderRows;
    private int invalidDates;
    private int invalidAmounts;
    private int blankPolicyNumbers;
    private int blankInsuranceCompanyCodes;
    private int blankValidToDates;
    private Long migrationRunId;
    private String sourceFileName;
    private String sourceSheetName;
    private String oracleSourceTable;
    private String targetTable;
    private String mappingSheetInspected;
    private final List<AssetInsuranceMigrationIssue> issues = new ArrayList<>();

    public static AssetInsuranceMigrationSummary base() {
        AssetInsuranceMigrationSummary summary = new AssetInsuranceMigrationSummary();
        summary.sourceFileName = AssetInsuranceMigrationService.SOURCE_FILE_NAME;
        summary.sourceSheetName = AssetInsuranceMigrationService.SOURCE_SHEET_NAME;
        summary.oracleSourceTable = AssetInsuranceMigrationService.ORACLE_SOURCE_TABLE;
        summary.targetTable = AssetInsuranceMigrationService.TARGET_TABLE;
        summary.mappingSheetInspected = AssetInsuranceMigrationService.MAPPING_FILE_NAME + " / " + AssetInsuranceMigrationService.MAPPING_SHEET_NAME;
        return summary;
    }

    public void addIssue(Integer excelRow, String referenceKey, String type, String reason) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(new AssetInsuranceMigrationIssue(excelRow, referenceKey, type, reason));
        }
    }

    public void completeProcessStatus() {
        completedWithWarnings = duplicates > 0
            || conflicts > 0
            || missingContracts > 0
            || missingAssets > 0
            || ambiguousAssets > 0;
        success = failed == 0;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public boolean isCompletedWithWarnings() { return completedWithWarnings; }
    public void setCompletedWithWarnings(boolean completedWithWarnings) { this.completedWithWarnings = completedWithWarnings; }
    public int getTotalSourceRows() { return totalSourceRows; }
    public void setTotalSourceRows(int totalSourceRows) { this.totalSourceRows = totalSourceRows; }
    public int getEligibleRows() { return eligibleRows; }
    public void incrementEligibleRows() { eligibleRows++; }
    public int getInserted() { return inserted; }
    public void incrementInserted() { inserted++; }
    public int getDuplicates() { return duplicates; }
    public void incrementDuplicates() { duplicates++; }
    public int getConflicts() { return conflicts; }
    public void incrementConflicts() { conflicts++; }
    public int getMissingContracts() { return missingContracts; }
    public void incrementMissingContracts() { missingContracts++; }
    public int getMissingAssets() { return missingAssets; }
    public void incrementMissingAssets() { missingAssets++; }
    public int getAmbiguousAssets() { return ambiguousAssets; }
    public void incrementAmbiguousAssets() { ambiguousAssets++; }
    public int getFailed() { return failed; }
    public void incrementFailed() { failed++; }
    public void setFailed(int failed) { this.failed = failed; }
    public int getSkippedBlankRows() { return skippedBlankRows; }
    public void incrementSkippedBlankRows() { skippedBlankRows++; }
    public int getRepeatedHeaderRows() { return repeatedHeaderRows; }
    public void incrementRepeatedHeaderRows() { repeatedHeaderRows++; }
    public int getInvalidDates() { return invalidDates; }
    public void incrementInvalidDates() { invalidDates++; }
    public int getInvalidAmounts() { return invalidAmounts; }
    public void incrementInvalidAmounts() { invalidAmounts++; }
    public int getBlankPolicyNumbers() { return blankPolicyNumbers; }
    public void incrementBlankPolicyNumbers() { blankPolicyNumbers++; }
    public int getBlankInsuranceCompanyCodes() { return blankInsuranceCompanyCodes; }
    public void incrementBlankInsuranceCompanyCodes() { blankInsuranceCompanyCodes++; }
    public int getBlankValidToDates() { return blankValidToDates; }
    public void incrementBlankValidToDates() { blankValidToDates++; }
    public Long getMigrationRunId() { return migrationRunId; }
    public void setMigrationRunId(Long migrationRunId) { this.migrationRunId = migrationRunId; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public String getOracleSourceTable() { return oracleSourceTable; }
    public void setOracleSourceTable(String oracleSourceTable) { this.oracleSourceTable = oracleSourceTable; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getMappingSheetInspected() { return mappingSheetInspected; }
    public void setMappingSheetInspected(String mappingSheetInspected) { this.mappingSheetInspected = mappingSheetInspected; }
    public List<AssetInsuranceMigrationIssue> getIssues() { return issues; }
}
