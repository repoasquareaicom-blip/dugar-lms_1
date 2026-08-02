package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.repository.ContractWorkflowRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ContractWorkflowService {

    private final ContractWorkflowRepository contractWorkflowRepository;

    public ContractWorkflowService(ContractWorkflowRepository contractWorkflowRepository) {
        this.contractWorkflowRepository = contractWorkflowRepository;
    }

    @Transactional
    public void submitForEdit(Long contractId, Authentication authentication) {
        contractWorkflowRepository.updateWorkflow(contractId, "SUBMITTED_FOR_EDIT", true, auditUser(authentication));
    }

    @Transactional
    public void submitToActive(Long contractId, Authentication authentication) {
        contractWorkflowRepository.updateWorkflow(contractId, "Y", false, auditUser(authentication));
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
