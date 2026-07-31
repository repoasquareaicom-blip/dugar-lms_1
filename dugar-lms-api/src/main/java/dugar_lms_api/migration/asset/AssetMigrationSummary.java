package dugar_lms_api.migration.asset;

import java.util.ArrayList;
import java.util.List;

public class AssetMigrationSummary {

    private static final int MAX_ISSUES = 100;

    private Long migrationRunId;
    private boolean success = true;
    private boolean completedWithWarnings;
    private String mappingSheetInspected;
    private String oracleSourceTable;
    private String sourceFileName;
    private String sourceSheetName;
    private String targetTable;
    private int totalSourceRows;
    private int eligibleRows;
    private int inserted;
    private int duplicates;
    private int conflicts;
    private int failed;
    private int missingContracts;
    private int blankRegistrationNumbers;
    private int blankEngineNumbers;
    private int blankChassisNumbers;
    private int repeatedHeaderRows;
    private int skippedBlankRows;
    private final List<AssetMigrationIssue> issues = new ArrayList<>();

    public static AssetMigrationSummary base() {
        AssetMigrationSummary summary = new AssetMigrationSummary();
        summary.mappingSheetInspected = AssetMigrationService.MAPPING_FILE_NAME + " / " + AssetMigrationService.MAPPING_SHEET_NAME;
        summary.oracleSourceTable = AssetMigrationService.PRIMARY_SOURCE_TABLE;
        summary.sourceFileName = AssetMigrationService.PRIMARY_SOURCE_FILE_NAME;
        summary.sourceSheetName = AssetMigrationService.PRIMARY_SOURCE_SHEET_NAME;
        summary.targetTable = AssetMigrationService.TARGET_TABLE;
        return summary;
    }

    public void addIssue(Integer excelRow, String referenceKey, String type, String reason) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(new AssetMigrationIssue(excelRow, referenceKey, type, reason));
        }
    }

    public Long getMigrationRunId() { return migrationRunId; }
    public void setMigrationRunId(Long migrationRunId) { this.migrationRunId = migrationRunId; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public boolean isCompletedWithWarnings() { return completedWithWarnings; }
    public void setCompletedWithWarnings(boolean completedWithWarnings) { this.completedWithWarnings = completedWithWarnings; }
    public String getMappingSheetInspected() { return mappingSheetInspected; }
    public void setMappingSheetInspected(String mappingSheetInspected) { this.mappingSheetInspected = mappingSheetInspected; }
    public String getOracleSourceTable() { return oracleSourceTable; }
    public void setOracleSourceTable(String oracleSourceTable) { this.oracleSourceTable = oracleSourceTable; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
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
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public void incrementFailed() { failed++; }
    public int getMissingContracts() { return missingContracts; }
    public void incrementMissingContracts() { missingContracts++; }
    public int getBlankRegistrationNumbers() { return blankRegistrationNumbers; }
    public void incrementBlankRegistrationNumbers() { blankRegistrationNumbers++; }
    public int getBlankEngineNumbers() { return blankEngineNumbers; }
    public void incrementBlankEngineNumbers() { blankEngineNumbers++; }
    public int getBlankChassisNumbers() { return blankChassisNumbers; }
    public void incrementBlankChassisNumbers() { blankChassisNumbers++; }
    public int getRepeatedHeaderRows() { return repeatedHeaderRows; }
    public void incrementRepeatedHeaderRows() { repeatedHeaderRows++; }
    public int getSkippedBlankRows() { return skippedBlankRows; }
    public void incrementSkippedBlankRows() { skippedBlankRows++; }
    public List<AssetMigrationIssue> getIssues() { return issues; }

    public void refreshWarningStatus() {
        completedWithWarnings = missingContracts > 0 || conflicts > 0 || duplicates > 0;
    }

    public void completeProcessStatus() {
        refreshWarningStatus();
        if (failed == 0) {
            success = true;
        }
    }
}
