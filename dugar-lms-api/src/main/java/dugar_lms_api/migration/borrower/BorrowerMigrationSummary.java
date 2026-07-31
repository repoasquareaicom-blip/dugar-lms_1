package dugar_lms_api.migration.borrower;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BorrowerMigrationSummary {

    private static final int MAX_ISSUES = 100;

    private Long migrationRunId;
    private boolean success = true;
    private String mappingSheetInspected;
    private String oracleSourceTable;
    private String sourceFileName;
    private String sourceSheetName;
    private Set<String> detectedSourceHeaders;
    private String partyCodeColumn;
    private String partyTypeColumn;
    private String targetTable;
    private int totalSourceRows;
    private int eligibleRows;
    private int borrowerRows;
    private int guarantorRows;
    private int inserted;
    private int duplicates;
    private int conflicts;
    private int failed;
    private int invalidClassificationRows;
    private int skippedBlankRows;
    private int repeatedHeaderRows;
    private int distinctContractHirerCodes;
    private int distinctContractGuarantorCodes;
    private int matchedHirerCodes;
    private int matchedGuarantorCodes;
    private int missingHirerCodes;
    private int missingGuarantorCodes;
    private int blankHirerCodes;
    private int blankGuarantorCodes;
    private final List<BorrowerMigrationIssue> issues = new ArrayList<>();

    public void addIssue(Integer excelRow, String partyCode, String type, String reason) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(new BorrowerMigrationIssue(excelRow, partyCode, type, reason));
        }
    }

    public Long getMigrationRunId() { return migrationRunId; }
    public void setMigrationRunId(Long migrationRunId) { this.migrationRunId = migrationRunId; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMappingSheetInspected() { return mappingSheetInspected; }
    public void setMappingSheetInspected(String mappingSheetInspected) { this.mappingSheetInspected = mappingSheetInspected; }
    public String getOracleSourceTable() { return oracleSourceTable; }
    public void setOracleSourceTable(String oracleSourceTable) { this.oracleSourceTable = oracleSourceTable; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public Set<String> getDetectedSourceHeaders() { return detectedSourceHeaders; }
    public void setDetectedSourceHeaders(Set<String> detectedSourceHeaders) { this.detectedSourceHeaders = detectedSourceHeaders; }
    public String getPartyCodeColumn() { return partyCodeColumn; }
    public void setPartyCodeColumn(String partyCodeColumn) { this.partyCodeColumn = partyCodeColumn; }
    public String getPartyTypeColumn() { return partyTypeColumn; }
    public void setPartyTypeColumn(String partyTypeColumn) { this.partyTypeColumn = partyTypeColumn; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public int getTotalSourceRows() { return totalSourceRows; }
    public void setTotalSourceRows(int totalSourceRows) { this.totalSourceRows = totalSourceRows; }
    public int getEligibleRows() { return eligibleRows; }
    public void incrementEligibleRows() { eligibleRows++; }
    public int getBorrowerRows() { return borrowerRows; }
    public void incrementBorrowerRows() { borrowerRows++; }
    public int getGuarantorRows() { return guarantorRows; }
    public void incrementGuarantorRows() { guarantorRows++; }
    public int getInserted() { return inserted; }
    public void incrementInserted() { inserted++; }
    public int getDuplicates() { return duplicates; }
    public void incrementDuplicates() { duplicates++; }
    public int getConflicts() { return conflicts; }
    public void incrementConflicts() { conflicts++; }
    public int getFailed() { return failed; }
    public void incrementFailed() { failed++; }
    public int getInvalidClassificationRows() { return invalidClassificationRows; }
    public void incrementInvalidClassificationRows() { invalidClassificationRows++; }
    public int getSkippedBlankRows() { return skippedBlankRows; }
    public void incrementSkippedBlankRows() { skippedBlankRows++; }
    public int getRepeatedHeaderRows() { return repeatedHeaderRows; }
    public void incrementRepeatedHeaderRows() { repeatedHeaderRows++; }
    public int getDistinctContractHirerCodes() { return distinctContractHirerCodes; }
    public void setDistinctContractHirerCodes(int distinctContractHirerCodes) { this.distinctContractHirerCodes = distinctContractHirerCodes; }
    public int getDistinctContractGuarantorCodes() { return distinctContractGuarantorCodes; }
    public void setDistinctContractGuarantorCodes(int distinctContractGuarantorCodes) { this.distinctContractGuarantorCodes = distinctContractGuarantorCodes; }
    public int getMatchedHirerCodes() { return matchedHirerCodes; }
    public void setMatchedHirerCodes(int matchedHirerCodes) { this.matchedHirerCodes = matchedHirerCodes; }
    public int getMatchedGuarantorCodes() { return matchedGuarantorCodes; }
    public void setMatchedGuarantorCodes(int matchedGuarantorCodes) { this.matchedGuarantorCodes = matchedGuarantorCodes; }
    public int getMissingHirerCodes() { return missingHirerCodes; }
    public void setMissingHirerCodes(int missingHirerCodes) { this.missingHirerCodes = missingHirerCodes; }
    public int getMissingGuarantorCodes() { return missingGuarantorCodes; }
    public void setMissingGuarantorCodes(int missingGuarantorCodes) { this.missingGuarantorCodes = missingGuarantorCodes; }
    public int getBlankHirerCodes() { return blankHirerCodes; }
    public void setBlankHirerCodes(int blankHirerCodes) { this.blankHirerCodes = blankHirerCodes; }
    public int getBlankGuarantorCodes() { return blankGuarantorCodes; }
    public void setBlankGuarantorCodes(int blankGuarantorCodes) { this.blankGuarantorCodes = blankGuarantorCodes; }
    public List<BorrowerMigrationIssue> getIssues() { return issues; }
}
