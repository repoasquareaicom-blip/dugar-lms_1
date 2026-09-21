package dugar_lms_api.modules.reports.demandlist;

import dugar_lms_api.modules.reports.aginganalysis.BranchWiseAgeingProcedureRepository;
import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
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

        assertThatThrownBy(() -> service.getDemandList(new DemandListRequest(null, null, null, null, null, null, null, null, null, null, null, null, 0, 25, null, null), null))
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

        DemandListResponse response = service.getDemandList(new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, "B", null, null, null, 0, 25, null, null), null);

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

        DemandListResponse response = service.getDemandList(new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, null, 1, null, null, 0, 25, null, null), null);

        assertThat(response.rows().content()).hasSize(1);
        assertThat(response.rows().content()).allMatch(row -> row.overdueInstallmentCount() == 1);
    }

    @Test
    void threeDuesAboveReportUsesProcedureOverdueCount() {
        DemandListService service = service(List.of(procedureRow(1L, "A", 2), procedureRow(2L, "B", 3), procedureRow(3L, "C", 4)));

        DemandListResponse response = service.getDemandList(new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, null, null, "THREE_DUES_ABOVE", null, 0, 25, null, null), null);

        assertThat(response.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("B", "C");
    }

    @Test
    void repossessedAndLitigationReportsUseExistingFlagCodes() {
        DemandListService service = service(List.of(
            procedureRow(1L, "A", 1, "REPOSSESSED_VEHICLE"),
            procedureRow(2L, "B", 1, "LITIGATION"),
            procedureRow(3L, "C", 1, "")
        ));

        DemandListResponse repossessed = service.getDemandList(new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, null, null, "REPOSSESSED_STOCK", null, 0, 25, null, null), null);
        DemandListResponse litigation = service.getDemandList(new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, null, null, "LITIGATION_MATTERS", null, 0, 25, null, null), null);

        assertThat(repossessed.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("A");
        assertThat(litigation.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("B");
    }

    @Test
    void commentAllowsBlankFollowUpDateButPtpRequiresDate() {
        DemandListRepository repository = mock(DemandListRepository.class);
        ContractAccessRepository accessRepository = mock(ContractAccessRepository.class);
        DemandListService service = new DemandListService(repository, new DemandListCalculationService(), mock(BranchWiseAgeingProcedureRepository.class), accessRepository);
        org.springframework.security.core.Authentication authentication = mock(org.springframework.security.core.Authentication.class);
        java.util.Map<String, Object> details = java.util.Map.of("userId", 42L);
        when(authentication.getDetails()).thenReturn(details);
        when(repository.addFollowUp(eq(1L), any(), eq(42L))).thenReturn(new ContractFollowUpDto(1L, 1L, "Call done", "COMMENT", null, 42L, "user", null));

        ContractFollowUpDto dto = service.addFollowUp(1L, new ContractFollowUpRequest("Call done", "COMMENT", null), authentication);

        assertThat(dto.followUpType()).isEqualTo("COMMENT");
        assertThatThrownBy(() -> service.addFollowUp(1L, new ContractFollowUpRequest("Promise", "PTP", null), authentication))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Promised date is required");
    }

    private DemandListRequest request(int page, int size, String sortColumn, String sortDirection) {
        return new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, null, null, null, null, page, size, sortColumn, sortDirection);
    }

    private DemandListService service(List<BranchWiseAgeingProcedureRepository.ProcedureContractReportRow> rows) {
        BranchWiseAgeingProcedureRepository procedureRepository = mock(BranchWiseAgeingProcedureRepository.class);
        when(procedureRepository.getContractReportRows(eq(LocalDate.of(2026, 1, 1)), eq("AREA"), any())).thenReturn(rows);
        return new DemandListService(mock(DemandListRepository.class), new DemandListCalculationService(), procedureRepository, mock(ContractAccessRepository.class));
    }

    private BranchWiseAgeingProcedureRepository.ProcedureContractReportRow procedureRow(Long id, String loanNumber, int overdueCount) {
        return procedureRow(id, loanNumber, overdueCount, "");
    }

    private BranchWiseAgeingProcedureRepository.ProcedureContractReportRow procedureRow(Long id, String loanNumber, int overdueCount, String flagCodes) {
        return new BranchWiseAgeingProcedureRepository.ProcedureContractReportRow(
            id,
            loanNumber,
            "HP",
            "AREA",
            null,
            "B" + loanNumber,
            "Borrower " + loanNumber,
            null,
            null,
            "G" + loanNumber,
            "Guarantor " + loanNumber,
            null,
            null,
            null,
            null,
            null,
            null,
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
            null,
            flagCodes == null || flagCodes.isBlank() ? 0 : 1,
            flagCodes == null || flagCodes.isBlank() ? "" : flagCodes,
            flagCodes,
            flagCodes == null || flagCodes.isBlank() ? "" : "Flag: Remark",
            0,
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
            null,
            null,
            "G" + loanNumber,
            "Guarantor " + loanNumber,
            null,
            null,
            null,
            null,
            null,
            null,
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
            null,
            0,
            "",
            "",
            "",
            0,
            "Current"
        );
    }
}
