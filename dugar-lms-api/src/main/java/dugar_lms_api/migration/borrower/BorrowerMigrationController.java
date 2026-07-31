package dugar_lms_api.migration.borrower;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/migration/borrowers", "/api/migrations/borrowers"})
public class BorrowerMigrationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BorrowerMigrationController.class);

    private final BorrowerMigrationService borrowerMigrationService;

    public BorrowerMigrationController(BorrowerMigrationService borrowerMigrationService) {
        this.borrowerMigrationService = borrowerMigrationService;
    }

    @PostMapping("/import")
    public ResponseEntity<BorrowerMigrationSummary> importBorrowersAndGuarantors() {
        BorrowerMigrationSummary summary = borrowerMigrationService.importBorrowersAndGuarantors();
        HttpStatus status = summary.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(summary);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BorrowerMigrationSummary> handleBadRequest(IllegalArgumentException exception) {
        LOGGER.error("Borrower and guarantor migration failed", exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(failure(rootMessage(exception)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BorrowerMigrationSummary> handleUnexpected(Exception exception) {
        LOGGER.error("Borrower and guarantor migration failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(failure(rootMessage(exception)));
    }

    private BorrowerMigrationSummary failure(String reason) {
        BorrowerMigrationSummary summary = new BorrowerMigrationSummary();
        summary.setSuccess(false);
        summary.setMappingSheetInspected(BorrowerMigrationService.MAPPING_FILE_NAME + " / " + BorrowerMigrationService.MAPPING_SHEET_NAME);
        summary.setOracleSourceTable(BorrowerMigrationService.SOURCE_TABLE);
        summary.setSourceFileName(BorrowerMigrationService.SOURCE_FILE_NAME);
        summary.setSourceSheetName(BorrowerMigrationService.SOURCE_SHEET_NAME);
        summary.setPartyCodeColumn("PARTY_CODE");
        summary.setPartyTypeColumn("PARTY_TYPE");
        summary.setTargetTable(BorrowerMigrationService.TARGET_TABLE);
        summary.addIssue(null, null, "FAILED", reason);
        return summary;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Borrower and guarantor migration failed" : current.getMessage();
    }
}
