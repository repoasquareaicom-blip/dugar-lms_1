package dugar_lms_api.modules.reports.repaymentschedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RepaymentScheduleData(Header header, List<Slab> slabs, List<Row> rows, Totals totals) {
    public RepaymentScheduleData(Header header, List<Slab> slabs, List<Row> rows) {
        this(header, slabs, rows, new Totals(
            rows.stream().map(Row::emiAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
            rows.stream().map(Row::interestAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
            rows.stream().map(Row::principalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
    public record Totals(BigDecimal emi, BigDecimal interest, BigDecimal principal) {}
    public record Header(Long contractId, String contractNumber, String borrowerName,
                         LocalDate contractDate, BigDecimal loanAmount, BigDecimal irrRate,
                         Integer tenureMonths, LocalDate firstEmiDate) {}
    public record Slab(int sequenceNo, int installments, BigDecimal amount) {}
    public record Row(int installmentNo, LocalDate dueDate, BigDecimal openingPrincipal,
                      BigDecimal emiAmount, BigDecimal interestAmount, BigDecimal principalAmount,
                      BigDecimal closingPrincipal, BigDecimal roundingAdjustment) {}
}
