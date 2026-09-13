package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.contracts.dto.ContractAreaOptionDto;
import dugar_lms_api.modules.contracts.dto.ContractListDto;
import dugar_lms_api.modules.contracts.repository.ContractListRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContractListService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 250;

    private final ContractListRepository contractListRepository;

    public ContractListService(ContractListRepository contractListRepository) {
        this.contractListRepository = contractListRepository;
    }

    public PageResponse<ContractListDto> getContracts(ContractListCriteria criteria) {
        return getContracts(criteria, null);
    }

    public PageResponse<ContractListDto> getContracts(ContractListCriteria criteria, Authentication authentication) {
        ContractListCriteria validatedCriteria = validate(criteria);
        ReportAccessScope accessScope = ReportAccessScope.from(authentication);

        long totalElements = accessScope.restrictedToUserGroup()
            ? contractListRepository.count(validatedCriteria, accessScope)
            : contractListRepository.count(validatedCriteria);
        List<ContractListDto> content = accessScope.restrictedToUserGroup()
            ? contractListRepository.find(validatedCriteria, accessScope)
            : contractListRepository.find(validatedCriteria);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / validatedCriteria.size());

        return new PageResponse<>(
            content,
            validatedCriteria.page(),
            validatedCriteria.size(),
            totalElements,
            totalPages,
            validatedCriteria.page() == 0,
            totalPages == 0 || validatedCriteria.page() >= totalPages - 1,
            content.size()
        );
    }

    public List<ContractAreaOptionDto> getAreas(String keyword, Integer limit) {
        return getAreas(keyword, limit, null);
    }

    public List<ContractAreaOptionDto> getAreas(String keyword, Integer limit, Authentication authentication) {
        ReportAccessScope accessScope = ReportAccessScope.from(authentication);
        if (accessScope.restrictedToUserGroup()) {
            return contractListRepository.findAreas(normalize(keyword), limit == null ? 20 : limit, accessScope);
        }
        return contractListRepository.findAreas(normalize(keyword), limit == null ? 20 : limit);
    }

    public List<ContractAreaOptionDto> getAreaMasterOptions(String keyword, Integer limit) {
        return contractListRepository.findAreaMasterOptions(normalize(keyword), limit == null ? 20 : limit);
    }

    private ContractListCriteria validate(ContractListCriteria criteria) {
        return new ContractListCriteria(
            normalize(criteria.keyword()),
            normalize(criteria.branch()),
            normalizeStatus(criteria.status()),
            normalize(criteria.product()),
            normalize(criteria.customerName()),
            criteria.contractDateFrom(),
            criteria.contractDateTo(),
            criteria.minimumLoanAmount(),
            criteria.maximumLoanAmount(),
            criteria.isDraft(),
            normalizeStatus(criteria.workflowStatus()),
            resolvePage(criteria.page()),
            resolveSize(criteria.size()),
            ContractListSortField.fromApiName(criteria.sortColumn()).apiName(),
            ContractListSortDirection.fromApiValue(criteria.sortDirection()).apiValue()
        );
    }

    private int resolvePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        return switch (normalized.toUpperCase()) {
            case "DRAFT" -> "D";
            case "SEND_FOR_EDIT", "SEND FOR EDIT", "SUBMITTED_FOR_EDIT" -> "E";
            case "ACTIVE" -> "Y";
            case "INACTIVE", "IN ACTIVE" -> "N";
            default -> normalized;
        };
    }
}
