package dugar_lms_api.migration.voucher;

import java.util.ArrayList;
import java.util.List;

public class VoucherMigrationSummary {

    private static final int MAX_ISSUES = 100;

    private Long migrationRunId;
    private boolean success = true;
    private boolean completedWithWarnings;
    private String mappingWorkbook;
    private String headerSourceFile;
    private String detailSourceFile;
    private String targetTables;
    private int totalSourceRows;
    private int headerRows;
    private int detailRows;
    private int inserted;
    private int duplicates;
    private int warnings;
    private int failed;
    private int missingContracts;
    private int missingLedgers;
    private int missingParties;
    private final List<VoucherMigrationIssue> issues = new ArrayList<>();

    public static VoucherMigrationSummary base() {
        VoucherMigrationSummary summary = new VoucherMigrationSummary();
        summary.mappingWorkbook = VoucherMigrationService.MAPPING_FILE_NAME;
        summary.headerSourceFile = VoucherMigrationService.HEADER_FILE_NAME;
        summary.detailSourceFile = VoucherMigrationService.DETAIL_FILE_NAME;
        summary.targetTables = "voucher_headers, voucher_details";
        return summary;
    }

    public void addIssue(Integer excelRow, String referenceKey, String type, String reason) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(new VoucherMigrationIssue(excelRow, referenceKey, type, reason));
        }
    }

    public void completeProcessStatus() {
        completedWithWarnings = duplicates > 0 || warnings > 0 || missingContracts > 0 || missingLedgers > 0 || missingParties > 0;
        success = failed == 0;
    }

    public Long getMigrationRunId() { return migrationRunId; }
    public void setMigrationRunId(Long migrationRunId) { this.migrationRunId = migrationRunId; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public boolean isCompletedWithWarnings() { return completedWithWarnings; }
    public String getMappingWorkbook() { return mappingWorkbook; }
    public String getHeaderSourceFile() { return headerSourceFile; }
    public String getDetailSourceFile() { return detailSourceFile; }
    public String getTargetTables() { return targetTables; }
    public int getTotalSourceRows() { return totalSourceRows; }
    public void setTotalSourceRows(int totalSourceRows) { this.totalSourceRows = totalSourceRows; }
    public int getHeaderRows() { return headerRows; }
    public void setHeaderRows(int headerRows) { this.headerRows = headerRows; }
    public int getDetailRows() { return detailRows; }
    public void setDetailRows(int detailRows) { this.detailRows = detailRows; }
    public int getInserted() { return inserted; }
    public void incrementInserted() { inserted++; }
    public int getDuplicates() { return duplicates; }
    public void incrementDuplicates() { duplicates++; }
    public int getWarnings() { return warnings; }
    public void incrementWarnings() { warnings++; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public void incrementFailed() { failed++; }
    public int getMissingContracts() { return missingContracts; }
    public void incrementMissingContracts() { missingContracts++; }
    public int getMissingLedgers() { return missingLedgers; }
    public void incrementMissingLedgers() { missingLedgers++; }
    public int getMissingParties() { return missingParties; }
    public void incrementMissingParties() { missingParties++; }
    public List<VoucherMigrationIssue> getIssues() { return issues; }
}
