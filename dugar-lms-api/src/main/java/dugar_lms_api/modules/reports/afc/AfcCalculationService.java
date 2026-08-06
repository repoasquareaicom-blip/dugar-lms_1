package dugar_lms_api.modules.reports.afc;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AfcCalculationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal AFC_RATE = new BigDecimal("36");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS = new BigDecimal("365");

    public List<AfcReportRowDto> calculateRows(
        AfcReportSource source,
        List<AfcRepaymentSlab> slabs,
        List<AfcReceipt> receipts,
        LocalDate asOnDate
    ) {
        List<ScheduleItem> schedule = schedule(source.emiStartDate(), source.paymentFrequency(), slabs);
        List<ReceiptBalance> receiptBalances = receipts.stream()
            .sorted(Comparator.comparing(AfcReceipt::receiptDate, Comparator.nullsLast(LocalDate::compareTo)))
            .map(receipt -> new ReceiptBalance(receipt.receiptDate(), receipt.receiptNumber(), money(receipt.amount())))
            .toList();

        BigDecimal previousAfc = ZERO;
        BigDecimal cumulativeReceipts = ZERO;
        List<AfcReportRowDto> rows = new ArrayList<>();

        for (int index = 0; index < schedule.size(); index++) {
            ScheduleItem item = schedule.get(index);
            Allocation allocation = allocate(item.amount(), receiptBalances);
            cumulativeReceipts = cumulativeReceipts.add(allocation.paidAmount()).setScale(2, RoundingMode.HALF_UP);

            boolean fullyPaid = allocation.paidAmount().compareTo(item.amount()) >= 0;
            int delayDays = delayDays(item.dueDate(), allocation.lastPaidDate(), asOnDate, fullyPaid);
            BigDecimal afcAmount = afc(item.amount(), previousAfc, delayDays);
            previousAfc = afcAmount;

            rows.add(new AfcReportRowDto(
                index + 1,
                item.dueDate(),
                allocation.lastPaidDate(),
                item.amount(),
                allocation.paidAmount(),
                String.join(", ", allocation.receiptNumbers()),
                delayDays,
                afcAmount,
                money(source.contractValue()).subtract(cumulativeReceipts).max(ZERO).setScale(2, RoundingMode.HALF_UP),
                currentAccountBalance()
            ));
        }

        return rows;
    }

    public AfcReportHeaderDto header(AfcReportSource source, LocalDate asOnDate) {
        return new AfcReportHeaderDto(
            source.contractId(),
            source.loanNumber(),
            source.customerName(),
            source.productType(),
            money(source.loanAmount()),
            money(source.financeCharges()),
            money(source.contractValue()),
            source.emiStartDate(),
            asOnDate
        );
    }

    public List<String> warnings(AfcReportSource source, List<AfcRepaymentSlab> slabs) {
        List<String> warnings = new ArrayList<>();
        if (source.emiStartDate() == null) {
            warnings.add("EMI start date missing");
        }
        if (slabs == null || slabs.isEmpty()) {
            warnings.add("Repayment structure missing");
        }
        return warnings;
    }

    private Allocation allocate(BigDecimal dueAmount, List<ReceiptBalance> receipts) {
        BigDecimal remainingDue = money(dueAmount);
        BigDecimal paid = ZERO;
        LocalDate lastPaidDate = null;
        List<String> receiptNumbers = new ArrayList<>();

        for (ReceiptBalance receipt : receipts) {
            if (remainingDue.compareTo(ZERO) <= 0) {
                break;
            }
            if (receipt.remaining().compareTo(ZERO) <= 0) {
                continue;
            }
            BigDecimal applied = receipt.remaining().min(remainingDue);
            receipt.apply(applied);
            remainingDue = remainingDue.subtract(applied).setScale(2, RoundingMode.HALF_UP);
            paid = paid.add(applied).setScale(2, RoundingMode.HALF_UP);
            lastPaidDate = receipt.receiptDate();
            if (receipt.receiptNumber() != null && !receipt.receiptNumber().isBlank() && !receiptNumbers.contains(receipt.receiptNumber())) {
                receiptNumbers.add(receipt.receiptNumber());
            }
        }

        return new Allocation(paid, lastPaidDate, receiptNumbers);
    }

    private int delayDays(LocalDate dueDate, LocalDate paidDate, LocalDate asOnDate, boolean fullyPaid) {
        if (dueDate == null) {
            return 0;
        }
        LocalDate comparisonDate = fullyPaid ? paidDate : asOnDate;
        if (comparisonDate == null || !comparisonDate.isAfter(dueDate)) {
            return 0;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(dueDate, comparisonDate));
    }

    private BigDecimal afc(BigDecimal emiAmount, BigDecimal previousAfcAmount, int delayDays) {
        return money(emiAmount)
            .add(money(previousAfcAmount))
            .multiply(AFC_RATE)
            .divide(HUNDRED, 10, RoundingMode.HALF_UP)
            .divide(DAYS, 10, RoundingMode.HALF_UP)
            .multiply(new BigDecimal(delayDays))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private List<ScheduleItem> schedule(LocalDate firstEmiDate, String frequency, List<AfcRepaymentSlab> slabs) {
        if (firstEmiDate == null || slabs == null || slabs.isEmpty()) {
            return List.of();
        }

        int months = frequencyMonths(frequency);
        LocalDate dueDate = firstEmiDate;
        List<ScheduleItem> schedule = new ArrayList<>();

        for (AfcRepaymentSlab slab : slabs.stream().sorted(Comparator.comparing(slab -> slab.sequenceNo() == null ? 0 : slab.sequenceNo())).toList()) {
            int count = slab.numberOfInstallments() == null ? 0 : slab.numberOfInstallments();
            BigDecimal amount = money(slab.installmentAmount());
            for (int i = 0; i < count; i++) {
                schedule.add(new ScheduleItem(dueDate, amount));
                dueDate = dueDate.plusMonths(months);
            }
        }
        return schedule;
    }

    private int frequencyMonths(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.contains("quarter")) return 3;
        if (normalized.contains("half") || normalized.contains("semi")) return 6;
        if (normalized.contains("year") || normalized.contains("annual")) return 12;
        return 1;
    }

    private BigDecimal currentAccountBalance() {
        return ZERO;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record ScheduleItem(LocalDate dueDate, BigDecimal amount) {
    }

    private record Allocation(BigDecimal paidAmount, LocalDate lastPaidDate, List<String> receiptNumbers) {
    }

    private static final class ReceiptBalance {
        private final LocalDate receiptDate;
        private final String receiptNumber;
        private BigDecimal remaining;

        private ReceiptBalance(LocalDate receiptDate, String receiptNumber, BigDecimal remaining) {
            this.receiptDate = receiptDate;
            this.receiptNumber = receiptNumber;
            this.remaining = remaining;
        }

        private LocalDate receiptDate() {
            return receiptDate;
        }

        private String receiptNumber() {
            return receiptNumber;
        }

        private BigDecimal remaining() {
            return remaining;
        }

        private void apply(BigDecimal amount) {
            remaining = remaining.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
