package dugar_lms_api.modules.reports.demandlist;

import dugar_lms_api.modules.reports.aginganalysis.BranchWiseAgeingProcedureRepository;
import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        assertThat(response.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("A", "B");
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
    void defaultSortKeepsCurrentOverdueCountDescendingBehaviour() {
        DemandListService service = service(List.of(
            procedureRow(1L, "A", 1),
            procedureRow(2L, "B", 3),
            procedureRow(3L, "C", 2)
        ));

        DemandListResponse response = service.getDemandList(request(0, 25, null, null), null);

        assertThat(response.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("B", "C", "A");
    }

    @Test
    void demandListSortsByOverdueCountAscendingAndDescending() {
        DemandListService service = service(List.of(
            procedureRow(1L, "A", 2),
            procedureRow(2L, "B", 1),
            procedureRow(3L, "C", 3)
        ));

        DemandListResponse ascending = service.getDemandList(request(0, 25, "overdueInstallmentCount", "asc"), null);
        DemandListResponse descending = service.getDemandList(request(0, 25, "overdueInstallmentCount", "desc"), null);

        assertThat(ascending.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("B", "A", "C");
        assertThat(descending.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("C", "A", "B");
    }

    @Test
    void demandListSortsByOverdueAmountAscendingAndDescending() {
        DemandListService service = service(List.of(
            procedureRow(1L, "A", 1, "", "500.00"),
            procedureRow(2L, "B", 1, "", "100.00"),
            procedureRow(3L, "C", 1, "", "900.00")
        ));

        DemandListResponse ascending = service.getDemandList(request(0, 25, "overdueAmount", "asc"), null);
        DemandListResponse descending = service.getDemandList(request(0, 25, "overdueAmount", "desc"), null);

        assertThat(ascending.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("B", "A", "C");
        assertThat(descending.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("C", "A", "B");
    }

    @Test
    void demandListSortsContractNumberNaturallyWithoutNumericCastingFailures() {
        DemandListService service = service(List.of(
            procedureRow(1L, "16950", 1),
            procedureRow(2L, "L-1019", 1),
            procedureRow(3L, "16026", 1),
            procedureRow(4L, " L-25 ", 1),
            procedureRow(5L, "L-649", 1),
            procedureRow(6L, "15939", 1),
            procedureRow(7L, "l-1", 1),
            procedureRow(8L, "ABC-2", 1)
        ));

        DemandListResponse ascending = service.getDemandList(request(0, 25, "contractNumber", "asc"), null);
        DemandListResponse descending = service.getDemandList(request(0, 25, "contractNumber", "desc"), null);

        assertThat(ascending.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("15939", "16026", "16950", "l-1", " L-25 ", "L-649", "L-1019", "ABC-2");
        assertThat(descending.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("L-1019", "L-649", " L-25 ", "l-1", "16950", "16026", "15939", "ABC-2");
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
    void demandListUsesAssetProductTypeAndVehicleTypeForProductUsage() {
        DemandListService service = service(List.of(procedureRow(17308L, "17308", 1)));

        DemandListRowDto row = service.getDemandList(request(0, 25, null, null), null).rows().content().get(0);

        assertThat(row.productType()).isEqualTo("Vehicles");
        assertThat(row.vehicleTypeCode()).isEqualTo("Private Car");
        assertThat(row.usage()).isEqualTo("Private Car");
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

        assertThat(response.rows().content()).extracting(DemandListRowDto::loanNumber).containsExactly("C", "B");
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
    void commentSavesWithoutFollowUpDate() {
        DemandListRepository repository = mock(DemandListRepository.class);
        ContractAccessRepository accessRepository = mock(ContractAccessRepository.class);
        DemandListService service = new DemandListService(repository, new DemandListCalculationService(), mock(BranchWiseAgeingProcedureRepository.class), accessRepository);
        org.springframework.security.core.Authentication authentication = authentication(42L);
        when(repository.addComment(eq(1L), eq("Call done"), eq(42L))).thenReturn(new ContractFollowUpDto(1L, 1L, "Call done", "COMMENT", null, 42L, "user", null));

        ContractFollowUpDto dto = service.addComment(1L, new ContractFollowUpRequest(" Call done ", null, null), authentication);

        assertThat(dto.followUpType()).isEqualTo("COMMENT");
        verify(repository).addComment(1L, "Call done", 42L);
    }

    @Test
    void ptpSaveCreatesHistoryRowForCurrentUser() {
        DemandListRepository repository = mock(DemandListRepository.class);
        ContractAccessRepository accessRepository = mock(ContractAccessRepository.class);
        DemandListService service = new DemandListService(repository, new DemandListCalculationService(), mock(BranchWiseAgeingProcedureRepository.class), accessRepository);
        org.springframework.security.core.Authentication authentication = authentication(42L);
        ContractPtpRequest request = new ContractPtpRequest(LocalDate.of(2026, 10, 5));
        when(repository.addPtp(eq(1L), eq(request), eq(42L))).thenReturn(new ContractPtpDto(10L, 1L, request.ptpDate(), 42L, "user", null));

        ContractPtpDto dto = service.addPtp(1L, request, authentication);

        assertThat(dto.ptpDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        verify(repository).addPtp(1L, request, 42L);
    }

    @Test
    void ptpDateIsMandatory() {
        DemandListService service = new DemandListService(mock(DemandListRepository.class), new DemandListCalculationService(), mock(BranchWiseAgeingProcedureRepository.class), mock(ContractAccessRepository.class));

        assertThatThrownBy(() -> service.addPtp(1L, new ContractPtpRequest(null), authentication(42L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PTP date is required");
    }

    @Test
    void demandListBulkLoadsLatestUserCommentAndPtp() {
        DemandListRepository repository = mock(DemandListRepository.class);
        BranchWiseAgeingProcedureRepository procedureRepository = mock(BranchWiseAgeingProcedureRepository.class);
        when(procedureRepository.getContractReportRows(eq(LocalDate.of(2026, 1, 1)), eq("AREA"), any())).thenReturn(List.of(
            procedureRow(1L, "A", 1),
            procedureRow(2L, "B", 1)
        ));
        when(repository.findLatestComments(eq(List.of(1L, 2L)), eq(42L))).thenReturn(Map.of(
            1L, new ContractFollowUpDto(1L, 1L, "Latest mine", "COMMENT", null, 42L, "user", null)
        ));
        when(repository.findLatestPtps(eq(List.of(1L, 2L)), eq(42L))).thenReturn(Map.of(
            2L, new ContractPtpDto(2L, 2L, LocalDate.of(2026, 10, 6), 42L, "user", null)
        ));
        DemandListService service = new DemandListService(repository, new DemandListCalculationService(), procedureRepository, mock(ContractAccessRepository.class));

        DemandListResponse response = service.getDemandList(request(0, 25, null, null), authentication(42L));

        assertThat(response.rows().content().get(0).latestComment()).isEqualTo("Latest mine");
        assertThat(response.rows().content().get(1).latestPtpDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        verify(repository, times(1)).findLatestComments(List.of(1L, 2L), 42L);
        verify(repository, times(1)).findLatestPtps(List.of(1L, 2L), 42L);
    }

    private DemandListRequest request(int page, int size, String sortColumn, String sortDirection) {
        return new DemandListRequest(LocalDate.of(2026, 1, 1), "AREA", null, null, null, null, null, null, null, null, null, null, page, size, sortColumn, sortDirection);
    }

    private DemandListService service(List<BranchWiseAgeingProcedureRepository.ProcedureContractReportRow> rows) {
        BranchWiseAgeingProcedureRepository procedureRepository = mock(BranchWiseAgeingProcedureRepository.class);
        when(procedureRepository.getContractReportRows(eq(LocalDate.of(2026, 1, 1)), eq("AREA"), any())).thenReturn(rows);
        return new DemandListService(mock(DemandListRepository.class), new DemandListCalculationService(), procedureRepository, mock(ContractAccessRepository.class));
    }

    private org.springframework.security.core.Authentication authentication(Long userId) {
        org.springframework.security.core.Authentication authentication = mock(org.springframework.security.core.Authentication.class);
        when(authentication.getDetails()).thenReturn(Map.of("userId", userId));
        return authentication;
    }

    private BranchWiseAgeingProcedureRepository.ProcedureContractReportRow procedureRow(Long id, String loanNumber, int overdueCount) {
        return procedureRow(id, loanNumber, overdueCount, "");
    }

    private BranchWiseAgeingProcedureRepository.ProcedureContractReportRow procedureRow(Long id, String loanNumber, int overdueCount, String flagCodes) {
        return procedureRow(id, loanNumber, overdueCount, flagCodes, "400.00");
    }

    private BranchWiseAgeingProcedureRepository.ProcedureContractReportRow procedureRow(Long id, String loanNumber, int overdueCount, String flagCodes, String overdueAmount) {
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
            "Vehicles",
            "Private Car",
            new BigDecimal("1200.00"),
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            new BigDecimal("12.00"),
            new BigDecimal("750.00"),
            new BigDecimal("250.00"),
            new BigDecimal("1000.00"),
            overdueCount,
            new BigDecimal(overdueAmount),
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
            "Vehicles",
            "Private Car",
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
