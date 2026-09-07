package dugar_lms_api.modules.reports.demandlist;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemandListCalculationServiceTest {

    private final DemandListCalculationService service = new DemandListCalculationService();

    @Test
    void contractWithOneRepaymentSlabCalculatesOverduesAndCurrentDue() {
        DemandListRowDto row = service.calculate(source("1000", "200", "1200", "0"), List.of(slab(1, 3, "400")), LocalDate.of(2026, 2, 15));

        assertThat(row.overdueInstallmentCount()).isEqualTo(2);
        assertThat(row.overdueAmount()).isEqualByComparingTo("800.00");
        assertThat(row.overdueFromDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(row.overdueEndDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(row.currentDueAmount()).isEqualByComparingTo("400.00");
        assertThat(row.currentDueDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void contractWithMultipleRepaymentSlabsUsesSlabsInSequence() {
        DemandListRowDto row = service.calculate(source("1000", "200", "1200", "0"), List.of(slab(1, 1, "300"), slab(2, 2, "450")), LocalDate.of(2026, 2, 15));

        assertThat(row.overdueAmount()).isEqualByComparingTo("750.00");
        assertThat(row.currentDueAmount()).isEqualByComparingTo("450.00");
    }

    @Test
    void authorisedReceiptIsAllocatedOldestInstallmentFirstAndSupportsPartialEmi() {
        DemandListRowDto row = service.calculate(source("1000", "200", "1200", "650"), List.of(slab(1, 3, "400")), LocalDate.of(2026, 3, 15));

        assertThat(row.overdueInstallmentCount()).isEqualTo(2);
        assertThat(row.overdueAmount()).isEqualByComparingTo("550.00");
        assertThat(row.overdueFromDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void demandListUsesFirstEmiDateForOverdueSchedule() {
        DemandListRowDto row = service.calculate(
            source("150000", "20220", "170220", "16000", LocalDate.of(2026, 7, 2)),
            List.of(slab(1, 12, "14185")),
            LocalDate.of(2026, 8, 8)
        );

        assertThat(row.overdueInstallmentCount()).isEqualTo(1);
        assertThat(row.overdueAmount()).isEqualByComparingTo("12370.00");
        assertThat(row.overdueFromDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(row.overdueEndDate()).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    void demandListReflectsAprilFirstEmiDateForContract16850Case() {
        DemandListRowDto row = service.calculate(
            source("150000", "20220", "170220", "16000", LocalDate.of(2026, 4, 1)),
            List.of(slab(1, 12, "14185")),
            LocalDate.of(2026, 8, 8)
        );

        assertThat(row.overdueInstallmentCount()).isEqualTo(4);
        assertThat(row.overdueAmount()).isEqualByComparingTo("54925.00");
        assertThat(row.overdueFromDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(row.overdueEndDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void fullyPaidContractShowsZeroOutstandingAndNoDemand() {
        DemandListRowDto row = service.calculate(source("1000", "200", "1200", "1200"), List.of(slab(1, 3, "400")), LocalDate.of(2026, 3, 15));

        assertThat(row.totalOutstanding()).isZero();
        assertThat(row.principalOutstanding()).isZero();
        assertThat(row.interestOutstanding()).isZero();
        assertThat(row.overdueAmount()).isZero();
    }

    @Test
    void overpaidContractDoesNotShowNegativeOutstanding() {
        DemandListRowDto row = service.calculate(source("1000", "200", "1200", "1500"), List.of(slab(1, 3, "400")), LocalDate.of(2026, 3, 15));

        assertThat(row.totalOutstanding()).isZero();
        assertThat(row.overdueAmount()).isZero();
    }

    @Test
    void principalInterestOutstandingAreProportionalAndRoundingDifferenceGoesToInterest() {
        DemandListRowDto row = service.calculate(source("100", "50", "150", "50"), List.of(slab(1, 1, "150")), LocalDate.of(2026, 1, 1));

        assertThat(row.totalOutstanding()).isEqualByComparingTo("100.00");
        assertThat(row.principalOutstanding()).isEqualByComparingTo("66.67");
        assertThat(row.interestOutstanding()).isEqualByComparingTo("33.33");
        assertThat(row.principalOutstanding().add(row.interestOutstanding())).isEqualByComparingTo(row.totalOutstanding());
    }

    @Test
    void missingRepaymentStructureAndEmiStartDateAreReportedAsWarnings() {
        DemandListRowDto row = service.calculate(source("1000", "200", "1200", "0", null), List.of(), LocalDate.of(2026, 1, 1));

        assertThat(row.warning()).contains("EMI start date missing", "Repayment structure missing");
    }

    @Test
    void missingOrZeroTotalContractValueAvoidsDivideByZero() {
        DemandListRowDto row = service.calculate(source("1000", "200", "0", "0"), List.of(slab(1, 1, "100")), LocalDate.of(2026, 1, 1));

        assertThat(row.principalOutstanding()).isZero();
        assertThat(row.interestOutstanding()).isZero();
        assertThat(row.warning()).contains("Total contract value missing or zero");
    }

    @Test
    void quarterlyFrequencyGeneratesQuarterlyDueDates() {
        DemandListRowDto row = service.calculate(source("1000", "200", "1200", "0", LocalDate.of(2026, 1, 1), "Quarterly"), List.of(slab(1, 2, "600")), LocalDate.of(2026, 2, 1));

        assertThat(row.overdueEndDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(row.currentDueDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void summaryTotalsAcrossFullFilteredDataset() {
        DemandListSummaryDto summary = service.summarize(List.of(
            service.calculate(source("1000", "200", "1200", "200"), List.of(slab(1, 3, "400")), LocalDate.of(2026, 2, 1)),
            service.calculate(source("500", "100", "600", "100"), List.of(slab(1, 2, "300")), LocalDate.of(2026, 1, 1))
        ));

        assertThat(summary.contractCount()).isEqualTo(2);
        assertThat(summary.totalAuthorisedReceipts()).isEqualByComparingTo("300.00");
        assertThat(summary.totalOutstanding()).isEqualByComparingTo("1500.00");
    }

    private DemandListSourceRow source(String loan, String finance, String total, String receipts) {
        return source(loan, finance, total, receipts, LocalDate.of(2026, 1, 1));
    }

    private DemandListSourceRow source(String loan, String finance, String total, String receipts, LocalDate firstEmiDate) {
        return source(loan, finance, total, receipts, firstEmiDate, "Monthly");
    }

    private DemandListSourceRow source(String loan, String finance, String total, String receipts, LocalDate firstEmiDate, String frequency) {
        return new DemandListSourceRow(1L, "L1", "B001", "Borrower", "G001", "Guarantor", "HP", "HP", "Asset", "VEH", "TN01", "1", "Use", bd(loan), bd(finance), bd("12"), bd(total), firstEmiDate, frequency, "AREA", null, "FO", bd(receipts));
    }

    private DemandListRepaymentSlab slab(int sequence, int count, String amount) {
        return new DemandListRepaymentSlab(1L, sequence, count, bd(amount));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
