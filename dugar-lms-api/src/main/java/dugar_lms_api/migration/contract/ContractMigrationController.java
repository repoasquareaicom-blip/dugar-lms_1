package dugar_lms_api.migration.contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration/contracts")
public class ContractMigrationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContractMigrationController.class);
    private static final String SHEET_NAME = "Active";

    private final ContractMigrationService contractMigrationService;

    public ContractMigrationController(ContractMigrationService contractMigrationService) {
        this.contractMigrationService = contractMigrationService;
    }

    @PostMapping("/prepare")
    public ResponseEntity<ContractMigrationResult> prepareContractMigration() {
        try {
            ContractMigrationResult result = contractMigrationService.prepareContractMigration();
            HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(result);
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Contract migration failed", exception);
            ContractMigrationResult result = ContractMigrationResult.failure(null, SHEET_NAME, rootMessage(exception));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception exception) {
            LOGGER.error("Contract migration failed", exception);
            ContractMigrationResult result = ContractMigrationResult.failure(null, SHEET_NAME, rootMessage(exception));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<ContractMigrationResult> importContracts() {
        try {
            ContractMigrationResult result = contractMigrationService.importContracts();
            HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(result);
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Contract migration failed", exception);
            ContractMigrationResult result = ContractMigrationResult.failure(null, SHEET_NAME, rootMessage(exception));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception exception) {
            LOGGER.error("Contract migration failed", exception);
            ContractMigrationResult result = ContractMigrationResult.failure(null, SHEET_NAME, rootMessage(exception));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ContractMigrationResult> handleBadRequest(IllegalArgumentException exception) {
        LOGGER.error("Contract migration failed", exception);
        ContractMigrationResult result = ContractMigrationResult.failure(null, SHEET_NAME, rootMessage(exception));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ContractMigrationResult> handleUnexpected(Exception exception) {
        LOGGER.error("Contract migration failed", exception);
        ContractMigrationResult result = ContractMigrationResult.failure(null, SHEET_NAME, rootMessage(exception));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Contract migration failed" : current.getMessage();
    }
}
