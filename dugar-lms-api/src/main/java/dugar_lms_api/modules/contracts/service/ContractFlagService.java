package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.ContractFlagMasterDto;
import dugar_lms_api.modules.contracts.dto.ContractFlagRequest;
import dugar_lms_api.modules.contracts.dto.ContractFlagResponse;
import dugar_lms_api.modules.contracts.dto.ContractFlagSelectionDto;
import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import dugar_lms_api.modules.contracts.repository.ContractFlagRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContractFlagService {

    private final ContractAccessRepository contractAccessRepository;
    private final ContractFlagRepository contractFlagRepository;

    public ContractFlagService(
        ContractAccessRepository contractAccessRepository,
        ContractFlagRepository contractFlagRepository
    ) {
        this.contractAccessRepository = contractAccessRepository;
        this.contractFlagRepository = contractFlagRepository;
    }

    public List<ContractFlagMasterDto> getMasterFlags() {
        return contractFlagRepository.findActiveMasterFlags();
    }

    public ContractFlagResponse getContractFlags(Long contractId, Authentication authentication) {
        requireContractAccess(contractId, authentication);
        return new ContractFlagResponse(contractId, contractFlagRepository.findByContractId(contractId));
    }

    @Transactional
    public ContractFlagResponse saveContractFlags(Long contractId, ContractFlagRequest request, Authentication authentication) {
        requireContractAccess(contractId, authentication);
        List<ContractFlagSelectionDto> flags = normalizeSelections(request == null ? null : request.flags());
        validateActiveFlags(flags);
        contractFlagRepository.replaceContractFlags(contractId, flags, auditUser(authentication));
        return new ContractFlagResponse(contractId, contractFlagRepository.findByContractId(contractId));
    }

    private void requireContractAccess(Long contractId, Authentication authentication) {
        if (contractId == null) {
            throw new IllegalArgumentException("Contract ID is required.");
        }
        if (!contractAccessRepository.canAccess(contractId, ReportAccessScope.from(authentication))) {
            throw new AccessDeniedException("Contract not found or not accessible.");
        }
    }

    private List<ContractFlagSelectionDto> normalizeSelections(List<ContractFlagSelectionDto> selections) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }

        Map<Long, ContractFlagSelectionDto> unique = new LinkedHashMap<>();
        for (ContractFlagSelectionDto selection : selections) {
            if (selection == null || selection.contractFlagMasterId() == null) {
                throw new IllegalArgumentException("Flag ID is required.");
            }
            unique.put(selection.contractFlagMasterId(), new ContractFlagSelectionDto(
                selection.contractFlagMasterId(),
                selection.remarks() == null ? null : selection.remarks().trim()
            ));
        }
        return new ArrayList<>(unique.values());
    }

    private void validateActiveFlags(List<ContractFlagSelectionDto> flags) {
        if (flags.isEmpty()) {
            return;
        }

        Set<Long> requestedIds = flags.stream()
            .map(ContractFlagSelectionDto::contractFlagMasterId)
            .collect(java.util.stream.Collectors.toSet());
        Set<Long> activeIds = contractFlagRepository.findActiveFlagIds(requestedIds);
        if (!activeIds.containsAll(requestedIds)) {
            throw new IllegalArgumentException("One or more selected flags are inactive or invalid.");
        }
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
