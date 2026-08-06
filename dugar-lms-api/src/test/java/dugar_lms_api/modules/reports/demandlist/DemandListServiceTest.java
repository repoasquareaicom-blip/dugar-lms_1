package dugar_lms_api.modules.reports.demandlist;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemandListServiceTest {

    @Test
    void asOnDateIsMandatory() {
        DemandListService service = service(List.of(), List.of());

        assertThatThrownBy(() -> service.getDemandList(new DemandListRequest(null, null, null, null, null, null, null, null, null, null, null, 0, 25, null, null), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("As On Date is required");
    }

    @Test
    void serverSidePagingAndSortingAreAppliedAfterCalculation() {
        DemandListService service = service(List.of(source(2L, "B"), source(1L, "A")), List.of(slab(1L), slab(2L)));

        DemandListResponse response = service.getDemandList(request(0, 1, "loanNumber", "asc"), null);

        assertThat(response.rows().totalElements()).isEqualTo(2);
        assertThat(response.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("A");
    }

    @Test
    void minimumAndMaximumOverdueFiltersAreAppliedInBackendService() {
        DemandListService service = service(List.of(source(1L, "A"), source(2L, "B")), List.of(slab(1L), slab(2L)));

        DemandListResponse response = service.getDemandList(new DemandListRequest(LocalDate.of(2026, 1, 1), null, null, null, null, null, new BigDecimal("100"), new BigDecimal("500"), null, null, null, 0, 25, null, null), null);

        assertThat(response.rows().content()).hasSize(2);
        assertThat(response.summary().contractCount()).isEqualTo(2);
    }

    @Test
    void printEndpointReturnsFullFilteredDataset() {
        DemandListService service = service(List.of(source(1L, "A"), source(2L, "B")), List.of(slab(1L), slab(2L)));

        DemandListResponse response = service.getPrintDemandList(request(0, 1, "loanNumber", "asc"), null);

        assertThat(response.rows().content()).hasSize(2);
        assertThat(response.rows().size()).isEqualTo(2);
    }

    @Test
    void overdueInstallmentCountFilterIsAppliedAfterCalculation() {
        DemandListService service = service(List.of(source(1L, "A"), source(2L, "B")), List.of(slab(1L), slab(2L)));

        DemandListResponse response = service.getDemandList(new DemandListRequest(LocalDate.of(2026, 1, 1), null, null, null, null, null, null, null, null, 1, null, 0, 25, null, null), null);

        assertThat(response.rows().content()).hasSize(2);
        assertThat(response.rows().content()).allMatch(row -> row.overdueInstallmentCount() == 1);
    }

    @Test
    void printRowLimitIsEnforced() {
        List<DemandListSourceRow> sources = new ArrayList<>();
        List<DemandListRepaymentSlab> slabs = new ArrayList<>();
        for (long id = 1; id <= DemandListService.PRINT_ROW_LIMIT + 1L; id++) {
            sources.add(source(id, "L" + id));
            slabs.add(slab(id));
        }
        DemandListService service = service(sources, slabs);

        DemandListResponse response = service.getPrintDemandList(request(0, 25, "loanNumber", "asc"), null);

        assertThat(response.printLimitExceeded()).isTrue();
        assertThat(response.message()).contains("more than 10,000 rows");
    }

    private DemandListRequest request(int page, int size, String sortColumn, String sortDirection) {
        return new DemandListRequest(LocalDate.of(2026, 1, 1), null, null, null, null, null, null, null, null, null, null, page, size, sortColumn, sortDirection);
    }

    private DemandListService service(List<DemandListSourceRow> sources, List<DemandListRepaymentSlab> slabs) {
        DemandListRepository repository = new StubDemandListRepository(sources, slabs);
        return new DemandListService(repository, new DemandListCalculationService());
    }

    private DemandListSourceRow source(Long id, String loanNumber) {
        return new DemandListSourceRow(id, loanNumber, "Borrower " + loanNumber, null, "HP", "HP", null, null, null, null, null, new BigDecimal("1000"), new BigDecimal("200"), new BigDecimal("1200"), LocalDate.of(2026, 1, 1), "Monthly", null, null, BigDecimal.ZERO);
    }

    private DemandListRepaymentSlab slab(Long contractId) {
        return new DemandListRepaymentSlab(contractId, 1, 1, new BigDecimal("400"));
    }

    private static class StubDemandListRepository extends DemandListRepository {
        private final List<DemandListSourceRow> sources;
        private final List<DemandListRepaymentSlab> slabs;

        StubDemandListRepository(List<DemandListSourceRow> sources, List<DemandListRepaymentSlab> slabs) {
            super(null);
            this.sources = sources;
            this.slabs = slabs;
        }

        @Override
        public List<DemandListSourceRow> findSourceRows(DemandListRequest request) {
            return sources;
        }

        @Override
        public List<DemandListRepaymentSlab> findRepaymentSlabs(List<Long> contractIds) {
            return slabs.stream().filter(slab -> contractIds.contains(slab.contractId())).toList();
        }
    }
}
