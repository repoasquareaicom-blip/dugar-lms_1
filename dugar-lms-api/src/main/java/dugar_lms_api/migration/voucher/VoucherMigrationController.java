package dugar_lms_api.migration.voucher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/migration/vouchers", "/api/migrations/vouchers"})
public class VoucherMigrationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoucherMigrationController.class);

    private final VoucherMigrationService voucherMigrationService;

    public VoucherMigrationController(VoucherMigrationService voucherMigrationService) {
        this.voucherMigrationService = voucherMigrationService;
    }

    @PostMapping("/prepare")
    public ResponseEntity<VoucherMigrationSummary> prepareVoucherMigration() {
        VoucherMigrationSummary summary = voucherMigrationService.prepareVoucherMigration();
        return ResponseEntity.status(summary.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(summary);
    }

    @PostMapping("/import")
    public ResponseEntity<VoucherMigrationSummary> importVouchers() {
        VoucherMigrationSummary summary = voucherMigrationService.importVouchers();
        return ResponseEntity.status(summary.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(summary);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<VoucherMigrationSummary> handleUnexpected(Exception exception) {
        LOGGER.error("Voucher migration failed", exception);
        VoucherMigrationSummary summary = VoucherMigrationSummary.base();
        summary.setSuccess(false);
        summary.addIssue(null, null, "FAILED", rootMessage(exception));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(summary);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Voucher migration failed" : current.getMessage();
    }
}
