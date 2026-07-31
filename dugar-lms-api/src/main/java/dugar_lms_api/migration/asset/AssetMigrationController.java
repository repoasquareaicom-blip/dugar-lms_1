package dugar_lms_api.migration.asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/migration/assets", "/api/migrations/assets"})
public class AssetMigrationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetMigrationController.class);

    private final AssetMigrationService assetMigrationService;

    public AssetMigrationController(AssetMigrationService assetMigrationService) {
        this.assetMigrationService = assetMigrationService;
    }

    @PostMapping("/prepare")
    public ResponseEntity<AssetMigrationSummary> prepareAssetMigration() {
        AssetMigrationSummary summary = assetMigrationService.prepareAssetMigration();
        HttpStatus status = summary.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        logSummary("Before returning from AssetMigrationController#prepareAssetMigration", summary);
        return ResponseEntity.status(status).body(summary);
    }

    @PostMapping("/import")
    public ResponseEntity<AssetMigrationSummary> importAssets() {
        AssetMigrationSummary summary = assetMigrationService.importAssets();
        HttpStatus status = summary.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        logSummary("Before returning from AssetMigrationController#importAssets", summary);
        return ResponseEntity.status(status).body(summary);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AssetMigrationSummary> handleUnexpected(Exception exception) {
        LOGGER.error("Asset migration failed", exception);
        AssetMigrationSummary summary = AssetMigrationSummary.base();
        summary.setSuccess(false);
        summary.addIssue(null, null, "FAILED", rootMessage(exception));
        logSummary("Before returning from AssetMigrationController#handleUnexpected", summary);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(summary);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Asset migration failed" : current.getMessage();
    }

    private void logSummary(String message, AssetMigrationSummary summary) {
        LOGGER.info(
            "{} summaryId={} success={} failed={} duplicates={} missingContracts={} completedWithWarnings={}",
            message,
            System.identityHashCode(summary),
            summary.isSuccess(),
            summary.getFailed(),
            summary.getDuplicates(),
            summary.getMissingContracts(),
            summary.isCompletedWithWarnings()
        );
    }
}
