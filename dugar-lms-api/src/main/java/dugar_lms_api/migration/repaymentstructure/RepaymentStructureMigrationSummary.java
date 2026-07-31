package dugar_lms_api.migration.repaymentstructure;

import java.util.ArrayList;
import java.util.List;

public class RepaymentStructureMigrationSummary {

    private static final int MAX_ISSUES = 100;

    private Long migrationRunId;
    private boolean success = true;
    private boolean completedWithWarnings;
    private String sourceFileName;
    private String sourceSheetName;
    private String oracleSourceTable;
    private String targetTable;
    private int totalSourceRows;
    private int eligibleRows;
    private int inserted;
    private int duplicates;
    private int conflicts;
    private int missingContracts;
    private int invalidSequences;
    private int invalidInstallmentCounts;
    private int invalidInstallmentAmounts;
    private int failed;
    private int skippedBlankRows;
    private int repeatedHeaderRows;
    private final List<RepaymentStructureMigrationIssue> issues = new ArrayList<>();

    public static RepaymentStructureMigrationSummary base() {
        RepaymentStructureMigrationSummary summary = new RepaymentStructureMigrationSummary();
        summary.sourceFileName = RepaymentStructureMigrationService.SOURCE_FILE_NAME;
        summary.sourceSheetName = RepaymentStructureMigrationService.SOURCE_SHEET_NAME;
        summary.oracleSourceTable = RepaymentStructureMigrationService.ORACLE_SOURCE_TABLE;
        summary.targetTable = RepaymentStructureMigrationService.TARGET_TABLE;
        return summary;
    }

    public void addIssue(Integer excelRow, String referenceKey, String type, String reason) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(new RepaymentStructureMigrationIssue(excelRow, referenceKey, type, reason));
        }
    }

    public void completeProcessStatus() {
        completedWithWarnings = duplicates > 0
            || conflicts > 0
            || missingContracts > 0
            || invalidSequences > 0
            || invalidInstallmentCounts > 0
            || invalidInstallmentAmounts > 0
            || failed > 0;
        success = true;
    }

    public Long getMigrationRunId() { return migrationRunId; }
    public void setMigrationRunId(Long migrationRunId) { this.migrationRunId = migrationRunId; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public boolean isCompletedWithWarnings() { return completedWithWarnings; }
    public void setCompletedWithWarnings(boolean completedWithWarnings) { this.completedWithWarnings = completedWithWarnings; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public String getOracleSourceTable() { return oracleSourceTable; }
    public void setOracleSourceTable(String oracleSourceTable) { this.oracleSourceTable = oracleSourceTable; }
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
    public int getMissingContracts() { return missingContracts; }
    public void incrementMissingContracts() { missingContracts++; }
    public int getInvalidSequences() { return invalidSequences; }
    public void incrementInvalidSequences() { invalidSequences++; }
    public int getInvalidInstallmentCounts() { return invalidInstallmentCounts; }
    public void incrementInvalidInstallmentCounts() { invalidInstallmentCounts++; }
    public int getInvalidInstallmentAmounts() { return invalidInstallmentAmounts; }
    public void incrementInvalidInstallmentAmounts() { invalidInstallmentAmounts++; }
    public int getFailed() { return failed; }
    public void incrementFailed() { failed++; }
    public void setFailed(int failed) { this.failed = failed; }
    public int getSkippedBlankRows() { return skippedBlankRows; }
    public void incrementSkippedBlankRows() { skippedBlankRows++; }
    public int getRepeatedHeaderRows() { return repeatedHeaderRows; }
    public void incrementRepeatedHeaderRows() { repeatedHeaderRows++; }
    public List<RepaymentStructureMigrationIssue> getIssues() { return issues; }
}
