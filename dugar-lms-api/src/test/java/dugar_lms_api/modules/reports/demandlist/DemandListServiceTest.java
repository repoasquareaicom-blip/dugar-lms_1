package dugar_lms_api.modules.reports.demandlist;

import dugar_lms_api.modules.reports.aginganalysis.BranchWiseAgeingProcedureRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemandListServiceTest {

    @Test
    void asOnDateIsMandatory() {
        DemandListService service = service(List.of());

        assertThatThrownBy(() -> service.getDemandList(new DemandListRequest(null, null, null, null, null, null, null, null, null, null, null, 0, 25, null, null), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("As On Date is required");
    }

    @Test
    void reportReturnsFullProcedureDatasetWithoutPaging() {
        DemandListService service = service(List.of(procedureRow(2L, "B", 1), procedureRow(1L, "A", 1)));

        DemandListResponse response = service.getDemandList(request(0, 1, "loanNumber", "asc"), null);

        assertThat(response.rows().totalElements()).isEqualTo(2);
        assertThat(response.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("B", "A");
        assertThat(response.rows().first()).isTrue();
        assertThat(response.rows().last()).isTrue();
    }

    @Test
    void contractNumberFilterIsAppliedAgainstProcedureRows() {
        DemandListService service = service(List.of(procedureRow(1L, "A", 1), procedureRow(2L, "B", 1)));

        DemandListResponse response = service.getDemandList(new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, "B", null, null, 0, 25, null, null), null);

        assertThat(response.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("B");
    }

    @Test
    void printEndpointReturnsFullFilteredDataset() {
        DemandListService service = service(List.of(procedureRow(1L, "A", 1), procedureRow(2L, "B", 1)));

        DemandListResponse response = service.getPrintDemandList(request(0, 1, "loanNumber", "asc"), null);

        assertThat(response.rows().content()).hasSize(2);
        assertThat(response.rows().size()).isEqualTo(2);
    }

    @Test
    void zeroOutstandingProcedureRowsRemainInDemandList() {
        DemandListService service = service(List.of(procedureRow(1L, "A", 1), zeroOutstandingProcedureRow(2L, "B")));

        DemandListResponse response = service.getDemandList(request(0, 25, null, null), null);

        assertThat(response.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("A", "B");
        assertThat(response.summary().contractCount()).isEqualTo(2);
        DemandListRowDto zeroOutstandingRow = response.rows().content().get(1);
        assertThat(zeroOutstandingRow.totalOutstanding()).isZero();
        assertThat(zeroOutstandingRow.overdueAmount()).isZero();
        assertThat(zeroOutstandingRow.currentDueAmount()).isZero();
    }

    @Test
    void overdueInstallmentCountFilterIsAppliedAfterCalculation() {
        DemandListService service = service(List.of(procedureRow(1L, "A", 1), procedureRow(2L, "B", 2)));

        DemandListResponse response = service.getDemandList(new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, null, 1, null, 0, 25, null, null), null);

        assertThat(response.rows().content()).hasSize(1);
        assertThat(response.rows().content()).allMatch(row -> row.overdueInstallmentCount() == 1);
    }

    private DemandListRequest request(int page, int size, String sortColumn, String sortDirection) {
        return new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, null, null, null, page, size, sortColumn, sortDirection);
    }

    private DemandListService service(List<BranchWiseAgeingProcedureRepository.ProcedureContractReportRow> rows) {
        BranchWiseAgeingProcedureRepository procedureRepository = mock(BranchWiseAgeingProcedureRepository.class);
        when(procedureRepository.getContractReportRows(eq(LocalDate.of(2026, 1, 1)), eq("AREA"), any())).thenReturn(rows);
        return new DemandListService(mock(DemandListRepository.class), new DemandListCalculationService(), procedureRepository);
    }

    private BranchWiseAgeingProcedureRepository.ProcedureContractReportRow procedureRow(Long id, String loanNumber, int overdueCount) {
        return new BranchWiseAgeingProcedureRepository.ProcedureContractReportRow(
            id,
            loanNumber,
            "HP",
            "AREA",
            null,
            "B" + loanNumber,
            "Borrower " + loanNumber,
            "G" + loanNumber,
            "Guarantor " + loanNumber,
            null,
            null,
            null,
            null,
            "OWN" + id,
            "HP",
            new BigDecimal("1200.00"),
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            new BigDecimal("12.00"),
            new BigDecimal("750.00"),
            new BigDecimal("250.00"),
            new BigDecimal("1000.00"),
            overdueCount,
            new BigDecimal("400.00"),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 1),
            BigDecimal.ZERO,
            null,
            overdueCount > 0 ? "1--30" : "Current"
        );
    }

    private BranchWiseAgeingProcedureRepository.ProcedureContractReportRow zeroOutstandingProcedureRow(Long id, String loanNumber) {
        return new BranchWiseAgeingProcedureRepository.ProcedureContractReportRow(
            id,
            loanNumber,
            "HP",
            "AREA",
            null,
            "B" + loanNumber,
            "Borrower " + loanNumber,
            "G" + loanNumber,
            "Guarantor " + loanNumber,
            null,
            null,
            null,
            null,
            "OWN" + id,
            "HP",
            new BigDecimal("1200.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("1200.00"),
            new BigDecimal("12.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            BigDecimal.ZERO,
            null,
            null,
            BigDecimal.ZERO,
            LocalDate.of(2026, 2, 1),
            "Current"
        );
    }
}
