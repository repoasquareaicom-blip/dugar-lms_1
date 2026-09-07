package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import dugar_lms_api.modules.contracts.repository.ContractWorkflowRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ContractWorkflowService {

    private final ContractWorkflowRepository contractWorkflowRepository;
    private final ContractAccessRepository contractAccessRepository;

    public ContractWorkflowService(
        ContractWorkflowRepository contractWorkflowRepository,
        ContractAccessRepository contractAccessRepository
    ) {
        this.contractWorkflowRepository = contractWorkflowRepository;
        this.contractAccessRepository = contractAccessRepository;
    }

    @Transactional
    public void submitForEdit(Long contractId, Authentication authentication) {
        updateStatus(contractId, "E", authentication);
    }

    @Transactional
    public void submitToActive(Long contractId, Authentication authentication) {
        updateStatus(contractId, "Y", authentication);
    }

    @Transactional
    public void updateStatus(Long contractId, String requestedStatus, Authentication authentication) {
        contractAccessRepository.requireAccess(contractId, ReportAccessScope.from(authentication));
        String status = normalizeStatus(requestedStatus);
        contractWorkflowRepository.updateWorkflow(contractId, status, isDraftStatus(status), auditUser(authentication));
    }

    private boolean isDraftStatus(String status) {
        return "D".equals(status) || "E".equals(status);
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Contract status is required");
        }

        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "D", "DRAFT" -> "D";
            case "E", "SEND_FOR_EDIT", "SEND FOR EDIT", "SUBMITTED_FOR_EDIT" -> "E";
            case "Y", "ACTIVE" -> "Y";
            case "N", "INACTIVE", "IN ACTIVE" -> "N";
            default -> throw new IllegalArgumentException("Unsupported contract status: " + value);
        };
    }

    private String auditUser(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) {
                return String.valueOf(userId);
            }
        }
        return authentication != null && authentication.getName() != null ? authentication.getName() : "system";
    }
}
