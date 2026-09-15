package dugar_lms_api.modules.contracts.controller;

import dugar_lms_api.modules.contracts.dto.ContractFlagMasterDto;
import dugar_lms_api.modules.contracts.dto.ContractFlagRequest;
import dugar_lms_api.modules.contracts.dto.ContractFlagResponse;
import dugar_lms_api.modules.contracts.service.ContractFlagService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contract-flags")
public class ContractFlagController {

    private final ContractFlagService contractFlagService;

    public ContractFlagController(ContractFlagService contractFlagService) {
        this.contractFlagService = contractFlagService;
    }

    @GetMapping("/master")
    public ResponseEntity<List<ContractFlagMasterDto>> masterFlags() {
        return ResponseEntity.ok(contractFlagService.getMasterFlags());
    }

    @GetMapping("/contract/{contractId}")
    public ResponseEntity<ContractFlagResponse> contractFlags(
        @PathVariable Long contractId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(contractFlagService.getContractFlags(contractId, authentication));
    }

    @PutMapping("/contract/{contractId}")
    public ResponseEntity<ContractFlagResponse> saveContractFlags(
        @PathVariable Long contractId,
        @RequestBody(required = false) ContractFlagRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(contractFlagService.saveContractFlags(contractId, request, authentication));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> validationError(IllegalArgumentException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> accessDenied(AccessDeniedException exception) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> dataAccessError(DataAccessException exception) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", "Unable to load contract flags. Please contact the administrator."));
    }
}
