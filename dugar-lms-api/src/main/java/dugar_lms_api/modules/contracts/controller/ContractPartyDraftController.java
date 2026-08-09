package dugar_lms_api.modules.contracts.controller;

import dugar_lms_api.modules.contracts.dto.ContractPartyDraftRequest;
import dugar_lms_api.modules.contracts.dto.ContractPartyDraftResponse;
import dugar_lms_api.modules.contracts.dto.ContractHeaderDraftDto;
import dugar_lms_api.modules.contracts.dto.ContractAssetDraftDto;
import dugar_lms_api.modules.contracts.dto.ContractCoLendingDraftDto;
import dugar_lms_api.modules.contracts.dto.ContractDocumentationDraftDto;
import dugar_lms_api.modules.contracts.dto.ContractFinancialDraftDto;
import dugar_lms_api.modules.contracts.dto.PartyDraftDto;
import dugar_lms_api.modules.contracts.dto.ContractWorkflowStatusRequest;
import dugar_lms_api.modules.contracts.service.ContractAssetDraftService;
import dugar_lms_api.modules.contracts.service.ContractCoLendingDraftService;
import dugar_lms_api.modules.contracts.service.ContractDocumentationDraftService;
import dugar_lms_api.modules.contracts.service.ContractFinancialDraftService;
import dugar_lms_api.modules.contracts.service.ContractPartyDraftService;
import dugar_lms_api.modules.contracts.service.ContractWorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts/draft")
public class ContractPartyDraftController {

    private final ContractPartyDraftService contractPartyDraftService;
    private final ContractAssetDraftService contractAssetDraftService;
    private final ContractFinancialDraftService contractFinancialDraftService;
    private final ContractDocumentationDraftService contractDocumentationDraftService;
    private final ContractCoLendingDraftService contractCoLendingDraftService;
    private final ContractWorkflowService contractWorkflowService;

    public ContractPartyDraftController(
        ContractPartyDraftService contractPartyDraftService,
        ContractAssetDraftService contractAssetDraftService,
        ContractFinancialDraftService contractFinancialDraftService,
        ContractDocumentationDraftService contractDocumentationDraftService,
        ContractCoLendingDraftService contractCoLendingDraftService,
        ContractWorkflowService contractWorkflowService
    ) {
        this.contractPartyDraftService = contractPartyDraftService;
        this.contractAssetDraftService = contractAssetDraftService;
        this.contractFinancialDraftService = contractFinancialDraftService;
        this.contractDocumentationDraftService = contractDocumentationDraftService;
        this.contractCoLendingDraftService = contractCoLendingDraftService;
        this.contractWorkflowService = contractWorkflowService;
    }

    @PostMapping("/parties")
    public ResponseEntity<ContractPartyDraftResponse> saveParties(
        @RequestBody ContractPartyDraftRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(contractPartyDraftService.saveParties(request, authentication));
    }

    @GetMapping("/{contractId}/parties")
    public ResponseEntity<List<PartyDraftDto>> getParties(@PathVariable Long contractId) {
        return ResponseEntity.ok(contractPartyDraftService.getPartyDetails(contractId));
    }

    @PostMapping("/{contractId}/header")
    public ResponseEntity<ContractHeaderDraftDto> saveHeader(
        @PathVariable Long contractId,
        @RequestBody ContractHeaderDraftDto request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(contractPartyDraftService.saveHeader(contractId, request, authentication));
    }

    @GetMapping("/{contractId}/asset")
    public ResponseEntity<ContractAssetDraftDto> getAsset(@PathVariable Long contractId) {
        ContractAssetDraftDto asset = contractAssetDraftService.getAssetDetails(contractId);
        return asset == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(asset);
    }

    @PostMapping("/{contractId}/asset")
    public ResponseEntity<ContractAssetDraftDto> saveAsset(
        @PathVariable Long contractId,
        @RequestBody ContractAssetDraftDto request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(contractAssetDraftService.saveAssetDetails(contractId, request, authentication));
    }

    @GetMapping("/{contractId}/financial")
    public ResponseEntity<ContractFinancialDraftDto> getFinancial(@PathVariable Long contractId) {
        ContractFinancialDraftDto financial = contractFinancialDraftService.getFinancialDetails(contractId);
        return financial == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(financial);
    }

    @PostMapping("/{contractId}/financial")
    public ResponseEntity<ContractFinancialDraftDto> saveFinancial(
        @PathVariable Long contractId,
        @RequestBody ContractFinancialDraftDto request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(contractFinancialDraftService.saveFinancialDetails(contractId, request, authentication));
    }

    @GetMapping("/{contractId}/documentation")
    public ResponseEntity<ContractDocumentationDraftDto> getDocumentation(@PathVariable Long contractId) {
        ContractDocumentationDraftDto documentation = contractDocumentationDraftService.getDocumentationDetails(contractId);
        return documentation == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(documentation);
    }

    @PostMapping("/{contractId}/documentation")
    public ResponseEntity<ContractDocumentationDraftDto> saveDocumentation(
        @PathVariable Long contractId,
        @RequestBody ContractDocumentationDraftDto request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(contractDocumentationDraftService.saveDocumentationDetails(contractId, request, authentication));
    }

    @GetMapping("/{contractId}/co-lending")
    public ResponseEntity<ContractCoLendingDraftDto> getCoLending(@PathVariable Long contractId) {
        ContractCoLendingDraftDto coLending = contractCoLendingDraftService.getCoLendingDetails(contractId);
        return coLending == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(coLending);
    }

    @PostMapping("/{contractId}/co-lending")
    public ResponseEntity<ContractCoLendingDraftDto> saveCoLending(
        @PathVariable Long contractId,
        @RequestBody ContractCoLendingDraftDto request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(contractCoLendingDraftService.saveCoLendingDetails(contractId, request, authentication));
    }

    @PostMapping("/{contractId}/submit-for-edit")
    public ResponseEntity<Void> submitForEdit(@PathVariable Long contractId, Authentication authentication) {
        contractWorkflowService.submitForEdit(contractId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{contractId}/submit-to-active")
    public ResponseEntity<Void> submitToActive(@PathVariable Long contractId, Authentication authentication) {
        contractWorkflowService.submitToActive(contractId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{contractId}/status")
    public ResponseEntity<Void> updateStatus(
        @PathVariable Long contractId,
        @RequestBody ContractWorkflowStatusRequest request,
        Authentication authentication
    ) {
        contractWorkflowService.updateStatus(contractId, request.status(), authentication);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> validationError(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", exception.getMessage()));
    }
}
