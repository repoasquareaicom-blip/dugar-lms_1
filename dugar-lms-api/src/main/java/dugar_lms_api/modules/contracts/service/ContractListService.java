package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.contracts.dto.ContractListDto;
import dugar_lms_api.modules.contracts.repository.ContractListRepository;
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
        ContractListCriteria validatedCriteria = validate(criteria);

        long totalElements = contractListRepository.count(validatedCriteria);
        List<ContractListDto> content = contractListRepository.find(validatedCriteria);
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

    private ContractListCriteria validate(ContractListCriteria criteria) {
        return new ContractListCriteria(
            normalize(criteria.keyword()),
            normalize(criteria.branch()),
            normalize(criteria.status()),
            normalize(criteria.product()),
            normalize(criteria.customerName()),
            criteria.contractDateFrom(),
            criteria.contractDateTo(),
            criteria.minimumLoanAmount(),
            criteria.maximumLoanAmount(),
            criteria.isDraft(),
            normalize(criteria.workflowStatus()),
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
}
