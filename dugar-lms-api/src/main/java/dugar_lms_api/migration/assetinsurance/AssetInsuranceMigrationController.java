package dugar_lms_api.migration.assetinsurance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/migration/asset-insurances", "/api/migrations/asset-insurances"})
public class AssetInsuranceMigrationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetInsuranceMigrationController.class);

    private final AssetInsuranceMigrationService assetInsuranceMigrationService;

    public AssetInsuranceMigrationController(AssetInsuranceMigrationService assetInsuranceMigrationService) {
        this.assetInsuranceMigrationService = assetInsuranceMigrationService;
    }

    @PostMapping("/prepare")
    public ResponseEntity<AssetInsuranceMigrationSummary> prepareAssetInsuranceMigration() {
        AssetInsuranceMigrationSummary summary = assetInsuranceMigrationService.prepareAssetInsuranceMigration();
        HttpStatus status = summary.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        logSummary("Before returning from AssetInsuranceMigrationController#prepareAssetInsuranceMigration", summary);
        return ResponseEntity.status(status).body(summary);
    }

    @PostMapping("/import")
    public ResponseEntity<AssetInsuranceMigrationSummary> importAssetInsurances() {
        AssetInsuranceMigrationSummary summary = assetInsuranceMigrationService.importAssetInsurances();
        HttpStatus status = summary.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        logSummary("Before returning from AssetInsuranceMigrationController#importAssetInsurances", summary);
        return ResponseEntity.status(status).body(summary);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AssetInsuranceMigrationSummary> handleUnexpected(Exception exception) {
        LOGGER.error("Asset insurance migration failed", exception);
        AssetInsuranceMigrationSummary summary = AssetInsuranceMigrationSummary.base();
        summary.setSuccess(false);
        summary.addIssue(null, null, "FAILED", rootMessage(exception));
        logSummary("Before returning from AssetInsuranceMigrationController#handleUnexpected", summary);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(summary);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Asset insurance migration failed" : current.getMessage();
    }

    private void logSummary(String message, AssetInsuranceMigrationSummary summary) {
        LOGGER.info(
            "{} success={} failed={} inserted={} duplicates={} conflicts={} missingContracts={} missingAssets={} ambiguousAssets={} completedWithWarnings={}",
            message,
            summary.isSuccess(),
            summary.getFailed(),
            summary.getInserted(),
            summary.getDuplicates(),
            summary.getConflicts(),
            summary.getMissingContracts(),
            summary.getMissingAssets(),
            summary.getAmbiguousAssets(),
            summary.isCompletedWithWarnings()
        );
    }
}
