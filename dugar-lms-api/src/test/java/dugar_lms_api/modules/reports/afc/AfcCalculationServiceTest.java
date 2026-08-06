package dugar_lms_api.modules.reports.afc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AfcCalculationServiceTest {

    private final AfcCalculationService service = new AfcCalculationService();

    @Test
    void generatesScheduleFromMultipleSlabs() {
        List<AfcReportRowDto> rows = service.calculateRows(source("1200"), List.of(slab(1, 1, "300"), slab(2, 2, "450")), List.of(), LocalDate.of(2026, 2, 15));

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(AfcReportRowDto::dueDate)
            .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
        assertThat(rows).extracting(AfcReportRowDto::dueAmount)
            .containsExactly(bd("300.00"), bd("450.00"), bd("450.00"));
    }

    @Test
    void allocatesReceiptAcrossMultipleEmisAndPartialPayment() {
        List<AfcReportRowDto> rows = service.calculateRows(source("1200"), List.of(slab(1, 3, "400")), List.of(receipt("2026-02-10", "R1", "650")), LocalDate.of(2026, 3, 15));

        assertThat(rows.get(0).emiAmountPaid()).isEqualByComparingTo("400.00");
        assertThat(rows.get(0).whenPaid()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(rows.get(1).emiAmountPaid()).isEqualByComparingTo("250.00");
        assertThat(rows.get(1).receiptNumber()).isEqualTo("R1");
    }

    @Test
    void supportsMultipleReceiptsForSingleEmi() {
        List<AfcReportRowDto> rows = service.calculateRows(source("400"), List.of(slab(1, 1, "400")), List.of(receipt("2026-01-05", "R1", "100"), receipt("2026-01-10", "R2", "300")), LocalDate.of(2026, 1, 31));

        assertThat(rows.get(0).emiAmountPaid()).isEqualByComparingTo("400.00");
        assertThat(rows.get(0).receiptNumber()).isEqualTo("R1, R2");
        assertThat(rows.get(0).whenPaid()).isEqualTo(LocalDate.of(2026, 1, 10));
    }

    @Test
    void delayDaysUsePaymentDateForPaidAndAsOnDateForUnpaid() {
        List<AfcReportRowDto> rows = service.calculateRows(source("800"), List.of(slab(1, 2, "400")), List.of(receipt("2026-01-20", "R1", "400")), LocalDate.of(2026, 3, 1));

        assertThat(rows.get(0).delayDays()).isEqualTo(19);
        assertThat(rows.get(1).delayDays()).isEqualTo(28);
    }

    @Test
    void paidBeforeDueDateHasZeroDelayAndZeroAfc() {
        List<AfcReportRowDto> rows = service.calculateRows(source("400"), List.of(slab(1, 1, "400")), List.of(receipt("2025-12-25", "R1", "400")), LocalDate.of(2026, 1, 1));

        assertThat(rows.get(0).delayDays()).isZero();
        assertThat(rows.get(0).afcAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void afcFormulaUsesPreviousAfcAmount() {
        List<AfcReportRowDto> rows = service.calculateRows(source("800"), List.of(slab(1, 2, "400")), List.of(), LocalDate.of(2026, 1, 11));

        assertThat(rows.get(0).afcAmount()).isEqualByComparingTo("3.95");
        assertThat(rows.get(1).afcAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void overpaymentDoesNotMakeNegativeContractBalance() {
        List<AfcReportRowDto> rows = service.calculateRows(source("800"), List.of(slab(1, 2, "400")), List.of(receipt("2026-01-01", "R1", "1000")), LocalDate.of(2026, 3, 1));

        assertThat(rows.get(1).contractBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void zeroPaymentLeavesPaidAmountZero() {
        List<AfcReportRowDto> rows = service.calculateRows(source("400"), List.of(slab(1, 1, "400")), List.of(), LocalDate.of(2026, 1, 5));

        assertThat(rows.get(0).emiAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(rows.get(0).contractBalance()).isEqualByComparingTo("400.00");
    }

    private AfcReportSource source(String contractValue) {
        return new AfcReportSource(1L, "L1", "Customer", "Vehicles", bd("600"), bd("200"), bd(contractValue), LocalDate.of(2026, 1, 1), "Monthly");
    }

    private AfcRepaymentSlab slab(int sequence, int count, String amount) {
        return new AfcRepaymentSlab(1L, sequence, count, bd(amount));
    }

    private AfcReceipt receipt(String date, String number, String amount) {
        return new AfcReceipt(LocalDate.parse(date), number, bd(amount));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
