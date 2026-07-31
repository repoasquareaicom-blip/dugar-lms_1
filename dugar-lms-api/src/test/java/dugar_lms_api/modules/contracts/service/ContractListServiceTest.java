package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.contracts.dto.ContractListDto;
import dugar_lms_api.modules.contracts.repository.ContractListRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractListServiceTest {

    @Mock
    private ContractListRepository contractListRepository;

    @InjectMocks
    private ContractListService contractListService;

    @Test
    void defaultRequestUsesContractsOrderedByContractIdDesc() {
        when(contractListRepository.count(any())).thenReturn(0L);
        when(contractListRepository.find(any())).thenReturn(List.of());

        PageResponse<ContractListDto> response = contractListService.getContracts(criteria(null, null, null, null, null));

        ArgumentCaptor<ContractListCriteria> criteriaCaptor = ArgumentCaptor.forClass(ContractListCriteria.class);
        verify(contractListRepository).count(criteriaCaptor.capture());

        ContractListCriteria criteria = criteriaCaptor.getValue();
        assertThat(criteria.page()).isEqualTo(0);
        assertThat(criteria.size()).isEqualTo(25);
        assertThat(criteria.keyword()).isNull();
        assertThat(criteria.sortColumn()).isEqualTo("contractId");
        assertThat(criteria.sortDirection()).isEqualTo("desc");
        assertThat(response.totalPages()).isZero();
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
        assertThat(response.numberOfElements()).isZero();
    }

    @Test
    void trimsKeywordSearch() {
        when(contractListRepository.count(any())).thenReturn(1L);
        when(contractListRepository.find(any())).thenReturn(List.of(contract()));

        PageResponse<ContractListDto> response = contractListService.getContracts(criteria(" 14507 ", null, null, null, null));

        ArgumentCaptor<ContractListCriteria> criteriaCaptor = ArgumentCaptor.forClass(ContractListCriteria.class);
        verify(contractListRepository).find(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue().keyword()).isEqualTo("14507");
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void sortByLoanAmountAscending() {
        when(contractListRepository.count(any())).thenReturn(0L);
        when(contractListRepository.find(any())).thenReturn(List.of());

        contractListService.getContracts(criteria(null, null, null, "loanAmount", "asc"));

        ArgumentCaptor<ContractListCriteria> criteriaCaptor = ArgumentCaptor.forClass(ContractListCriteria.class);
        verify(contractListRepository).find(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue().sortColumn()).isEqualTo("loanAmount");
        assertThat(criteriaCaptor.getValue().sortDirection()).isEqualTo("asc");
    }

    @Test
    void invalidSortFieldFallsBackToContractIdDesc() {
        when(contractListRepository.count(any())).thenReturn(0L);
        when(contractListRepository.find(any())).thenReturn(List.of());

        contractListService.getContracts(criteria(null, null, null, "contract_id;drop table contracts", "sideways"));

        ArgumentCaptor<ContractListCriteria> criteriaCaptor = ArgumentCaptor.forClass(ContractListCriteria.class);
        verify(contractListRepository).find(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue().sortColumn()).isEqualTo("contractId");
        assertThat(criteriaCaptor.getValue().sortDirection()).isEqualTo("desc");
    }

    @Test
    void pageSizeCannotExceedMaximum() {
        when(contractListRepository.count(any())).thenReturn(0L);
        when(contractListRepository.find(any())).thenReturn(List.of());

        PageResponse<ContractListDto> response = contractListService.getContracts(new ContractListCriteria(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            500,
            null,
            null
        ));

        ArgumentCaptor<ContractListCriteria> criteriaCaptor = ArgumentCaptor.forClass(ContractListCriteria.class);
        verify(contractListRepository).find(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue().size()).isEqualTo(250);
        assertThat(response.size()).isEqualTo(250);
    }

    @Test
    void emptyResultReturnsValidPageMetadata() {
        when(contractListRepository.count(any())).thenReturn(0L);
        when(contractListRepository.find(any())).thenReturn(List.of());

        PageResponse<ContractListDto> response = contractListService.getContracts(new ContractListCriteria(
            "missing",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            2,
            25,
            null,
            null
        ));

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(25);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isTrue();
        assertThat(response.numberOfElements()).isZero();
    }

    private ContractListDto contract() {
        return new ContractListDto(
            14507L,
            "14507",
            "OLD-14507",
            "HP",
            "BR-01",
            LocalDate.of(2026, 1, 1),
            BigDecimal.TEN,
            new BigDecimal("125000.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("15000.00"),
            new BigDecimal("2500.00"),
            new BigDecimal("1000.00"),
            36,
            BigDecimal.ONE,
            LocalDate.of(2026, 2, 1),
            "1|12|12000.00;2|24|15000.00",
            "TN01AB1234",
            "Ashok Leyland",
            "TRUCK",
            "ENG123",
            "CHS123",
            "2024",
            "HYPOTHECATION",
            new BigDecimal("950000.00"),
            "Primary vehicle",
            "OWN001",
            LocalDate.of(2025, 12, 20),
            "CUST001",
            "Ravi Kumar",
            "9876543210",
            "ravi@example.com",
            "12 Mount Road",
            "Chennai",
            "Tamil Nadu",
            "600001",
            "ABCDE1234F",
            "Business",
            "GUA001",
            "Suresh Kumar",
            "9876501234",
            "suresh@example.com",
            "18 Anna Salai",
            "Chennai",
            "Tamil Nadu",
            "600002",
            "FGHIJ5678K",
            "Salaried"
        );
    }

    private ContractListCriteria criteria(String keyword, String product, BigDecimal minimumLoanAmount, String sortColumn, String sortDirection) {
        return new ContractListCriteria(
            keyword,
            null,
            null,
            product,
            null,
            null,
            null,
            minimumLoanAmount,
            null,
            null,
            null,
            sortColumn,
            sortDirection
        );
    }
}
