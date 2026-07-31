package dugar_lms_api.migration.repaymentstructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/migration/repayment-structures", "/api/migrations/repayment-structures"})
public class RepaymentStructureMigrationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepaymentStructureMigrationController.class);

    private final RepaymentStructureMigrationService repaymentStructureMigrationService;

    public RepaymentStructureMigrationController(RepaymentStructureMigrationService repaymentStructureMigrationService) {
        this.repaymentStructureMigrationService = repaymentStructureMigrationService;
    }

    @PostMapping("/prepare")
    public ResponseEntity<RepaymentStructureMigrationSummary> prepareRepaymentStructureMigration() {
        RepaymentStructureMigrationSummary summary = repaymentStructureMigrationService.prepareRepaymentStructureMigration();
        HttpStatus status = summary.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        logSummary("Before returning from RepaymentStructureMigrationController#prepareRepaymentStructureMigration", summary);
        return ResponseEntity.status(status).body(summary);
    }

    @PostMapping("/import")
    public ResponseEntity<RepaymentStructureMigrationSummary> importRepaymentStructures() {
        RepaymentStructureMigrationSummary summary = repaymentStructureMigrationService.importRepaymentStructures();
        HttpStatus status = summary.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        logSummary("Before returning from RepaymentStructureMigrationController#importRepaymentStructures", summary);
        return ResponseEntity.status(status).body(summary);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RepaymentStructureMigrationSummary> handleUnexpected(Exception exception) {
        LOGGER.error("Repayment structure migration failed", exception);
        RepaymentStructureMigrationSummary summary = RepaymentStructureMigrationSummary.base();
        summary.setSuccess(false);
        summary.addIssue(null, null, "FAILED", rootMessage(exception));
        logSummary("Before returning from RepaymentStructureMigrationController#handleUnexpected", summary);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(summary);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Repayment structure migration failed" : current.getMessage();
    }

    private void logSummary(String message, RepaymentStructureMigrationSummary summary) {
        LOGGER.info(
            "{} success={} failed={} inserted={} duplicates={} conflicts={} missingContracts={} completedWithWarnings={}",
            message,
            summary.isSuccess(),
            summary.getFailed(),
            summary.getInserted(),
            summary.getDuplicates(),
            summary.getConflicts(),
            summary.getMissingContracts(),
            summary.isCompletedWithWarnings()
        );
    }
}
