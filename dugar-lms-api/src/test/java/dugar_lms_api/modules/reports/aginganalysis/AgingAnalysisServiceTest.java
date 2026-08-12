package dugar_lms_api.modules.reports.aginganalysis;

import dugar_lms_api.modules.reports.demandlist.DemandListRequest;
import dugar_lms_api.modules.reports.demandlist.DemandListRowDto;
import dugar_lms_api.modules.reports.demandlist.DemandListService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgingAnalysisServiceTest {

    @Test
    void branchWiseRequiresAsOnDateOnly() {
        AgingAnalysisService service = service(mock(DemandListService.class), mock(BranchWiseAgeingProcedureRepository.class), mock(AgingAnalysisDrilldownRepository.class));

        assertThatThrownBy(() -> service.getAgingAnalysis(new AgingAnalysisRequest(null, null, null), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("As On Date is required");
    }

    @Test
    void branchWiseReportUsesStoredProcedureRows() {
        BranchWiseAgeingProcedureRepository procedureRepository = mock(BranchWiseAgeingProcedureRepository.class);
        LocalDate asOnDate = LocalDate.now();
        when(procedureRepository.getBranchWiseRows(asOnDate, "F0001"))
            .thenReturn(List.of(
                new AgingAnalysisBranchRowDto("F0001", 2, new BigDecimal("2000.00"), new BigDecimal("800.00"), new BigDecimal("1200.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2000.00"), new BigDecimal("200.00"))
            ));
        AgingAnalysisService service = service(mock(DemandListService.class), procedureRepository, mock(AgingAnalysisDrilldownRepository.class));

        AgingAnalysisResponse response = service.getAgingAnalysis(new AgingAnalysisRequest(asOnDate, "F0001", null), null);

        assertThat(response.summary().noOfAccounts()).isEqualTo(2);
        assertThat(response.branchWise()).hasSize(1);
        assertThat(response.summary().aum()).isEqualByComparingTo("2000.00");
        assertThat(response.consolidated())
            .filteredOn(row -> "1--30".equals(row.label()))
            .singleElement()
            .extracting(AgingAnalysisConsolidatedRowDto::total)
            .isEqualTo(new BigDecimal("1200.00"));
        assertThat(response.consolidated())
            .filteredOn(row -> "1--30".equals(row.label()))
            .singleElement()
            .extracting(AgingAnalysisConsolidatedRowDto::interestOutstanding)
            .isEqualTo(new BigDecimal("120.00"));
    }

    @Test
    void branchWiseProcedureReportRejectsContractNumberUntilProcedureSupportsIt() {
        AgingAnalysisService service = service(mock(DemandListService.class), mock(BranchWiseAgeingProcedureRepository.class), mock(AgingAnalysisDrilldownRepository.class));

        assertThatThrownBy(() -> service.getAgingAnalysis(new AgingAnalysisRequest(LocalDate.now(), "F0001", "8402"), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Contract No filter is not supported");
    }

    @Test
    void matrixReportsGroupByLoanTicketAndInterestRate() {
        BranchWiseAgeingProcedureRepository procedureRepository = mock(BranchWiseAgeingProcedureRepository.class);
        LocalDate asOnDate = LocalDate.of(2026, 8, 11);
        when(procedureRepository.getContractReportRows(asOnDate, "F0001"))
            .thenReturn(List.of(
                procedureRow(1L, "F0001", new BigDecimal("10000000.00"), new BigDecimal("12"), new BigDecimal("7500000.00"), new BigDecimal("1000.00"), LocalDate.of(2026, 7, 15)),
                procedureRow(2L, "F0001", new BigDecimal("500000.00"), new BigDecimal("12"), new BigDecimal("400000.00"), BigDecimal.ZERO, null)
            ));
        AgingAnalysisService service = service(mock(DemandListService.class), procedureRepository, mock(AgingAnalysisDrilldownRepository.class));

        AgingAnalysisMatrixResponse loanTicket = service.getLoanTicketWise(asOnDate, "F0001", null);
        AgingAnalysisMatrixResponse interestWise = service.getInterestWise(asOnDate, "F0001", null);

        assertThat(loanTicket.rows()).extracting(AgingAnalysisMatrixRowDto::label).contains("> 2 - 5", "> 10");
        assertThat(interestWise.rows()).extracting(AgingAnalysisMatrixRowDto::label).containsExactly("12%");
        assertThat(interestWise.rows().get(0).principalOutstanding()).isEqualByComparingTo("0.79");
    }

    @Test
    void matrixReportsAllowBlankAreaAndReturnAllProcedureRows() {
        BranchWiseAgeingProcedureRepository procedureRepository = mock(BranchWiseAgeingProcedureRepository.class);
        LocalDate asOnDate = LocalDate.of(2026, 8, 11);
        when(procedureRepository.getContractReportRows(asOnDate, null))
            .thenReturn(List.of(
                procedureRow(1L, "F0001", new BigDecimal("500000.00"), new BigDecimal("12"), new BigDecimal("400000.00"), BigDecimal.ZERO, null),
                procedureRow(2L, "F0002", new BigDecimal("1000000.00"), new BigDecimal("14"), new BigDecimal("700000.00"), BigDecimal.ZERO, null)
            ));
        AgingAnalysisService service = service(mock(DemandListService.class), procedureRepository, mock(AgingAnalysisDrilldownRepository.class));

        AgingAnalysisMatrixResponse response = service.getLoanTicketWise(asOnDate, null, null);

        assertThat(response.rows()).isNotEmpty();
    }

    private AgingAnalysisService service(
        DemandListService demandListService,
        BranchWiseAgeingProcedureRepository procedureRepository,
        AgingAnalysisDrilldownRepository drilldownRepository
    ) {
        return new AgingAnalysisService(demandListService, procedureRepository, drilldownRepository);
    }

    private DemandListRowDto row(Long contractId, String area, BigDecimal totalOutstanding, BigDecimal overdueAmount, LocalDate overdueFromDate) {
        BigDecimal principal = totalOutstanding.multiply(new BigDecimal("0.75"));
        BigDecimal interest = totalOutstanding.subtract(principal);
        return new DemandListRowDto(
            contractId,
            "LN" + contractId,
            "B" + contractId,
            "Borrower " + contractId,
            "G" + contractId,
            null,
            "HP",
            null,
            null,
            null,
            null,
            null,
            totalOutstanding,
            new BigDecimal("12"),
            totalOutstanding,
            BigDecimal.ZERO,
            principal,
            interest,
            totalOutstanding,
            overdueAmount.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0,
            overdueAmount,
            overdueFromDate,
            overdueFromDate,
            BigDecimal.ZERO,
            null,
            area,
            null,
            null
        );
    }

    private BranchWiseAgeingProcedureRepository.ProcedureContractReportRow procedureRow(
        Long contractId,
        String area,
        BigDecimal loanAmount,
        BigDecimal flatInterestRate,
        BigDecimal principalOutstanding,
        BigDecimal overdueAmount,
        LocalDate overdueFromDate
    ) {
        BigDecimal totalOutstanding = principalOutstanding.add(principalOutstanding.divide(new BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP));
        return new BranchWiseAgeingProcedureRepository.ProcedureContractReportRow(
            contractId,
            "LN" + contractId,
            "HP",
            area,
            "B" + contractId,
            "Borrower " + contractId,
            "G" + contractId,
            "Guarantor " + contractId,
            null,
            null,
            null,
            null,
            null,
            "HP",
            totalOutstanding,
            loanAmount,
            BigDecimal.ZERO,
            flatInterestRate,
            principalOutstanding,
            totalOutstanding.subtract(principalOutstanding),
            totalOutstanding,
            overdueAmount.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0,
            overdueAmount,
            overdueFromDate,
            overdueFromDate,
            BigDecimal.ZERO,
            null,
            overdueAmount.compareTo(BigDecimal.ZERO) > 0 ? "1--30" : "Current"
        );
    }
}
