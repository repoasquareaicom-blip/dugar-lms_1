package dugar_lms_api.modules.reports.demandlist;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DemandListCalculationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public DemandListRowDto calculate(DemandListSourceRow source, List<DemandListRepaymentSlab> slabs, LocalDate asOnDate) {
        BigDecimal totalContractValue = money(source.totalContractValue());
        BigDecimal loanAmount = money(source.loanAmount());
        BigDecimal financeCharges = money(source.financeCharges());
        BigDecimal authorisedReceipts = money(source.authorisedReceipts()).max(ZERO);

        List<Installment> schedule = schedule(source.firstEmiDate(), source.paymentFrequency(), slabs);
        String warning = warning(source, slabs, totalContractValue);

        BigDecimal totalOutstanding = totalContractValue.subtract(authorisedReceipts).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal principalOutstanding = proportional(totalOutstanding, loanAmount, totalContractValue);
        BigDecimal interestOutstanding = totalOutstanding.subtract(principalOutstanding).setScale(2, RoundingMode.HALF_UP);

        Allocation allocation = allocate(schedule, authorisedReceipts, asOnDate);

        return new DemandListRowDto(
            source.contractId(),
            source.loanNumber(),
            source.borrowerName(),
            source.guarantorName(),
            source.productType(),
            source.assetDescription(),
            source.vehicleTypeCode(),
            source.registrationOrLocation(),
            source.ownerNumber(),
            source.usage(),
            totalContractValue,
            authorisedReceipts,
            principalOutstanding,
            interestOutstanding,
            totalOutstanding,
            allocation.overdueInstallmentCount(),
            allocation.overdueAmount(),
            allocation.overdueFromDate(),
            allocation.overdueEndDate(),
            allocation.currentDueAmount(),
            allocation.currentDueDate(),
            source.area(),
            source.fieldOfficer(),
            warning
        );
    }

    public DemandListSummaryDto summarize(List<DemandListRowDto> rows) {
        BigDecimal contractValue = ZERO;
        BigDecimal receipts = ZERO;
        BigDecimal principal = ZERO;
        BigDecimal interest = ZERO;
        BigDecimal total = ZERO;
        BigDecimal overdue = ZERO;
        BigDecimal current = ZERO;

        for (DemandListRowDto row : rows) {
            contractValue = contractValue.add(money(row.contractValue()));
            receipts = receipts.add(money(row.authorisedReceipts()));
            principal = principal.add(money(row.principalOutstanding()));
            interest = interest.add(money(row.interestOutstanding()));
            total = total.add(money(row.totalOutstanding()));
            overdue = overdue.add(money(row.overdueAmount()));
            current = current.add(money(row.currentDueAmount()));
        }

        return new DemandListSummaryDto(
            rows.size(),
            money(contractValue),
            money(receipts),
            money(principal),
            money(interest),
            money(total),
            money(overdue),
            money(current)
        );
    }

    private Allocation allocate(List<Installment> schedule, BigDecimal receipts, LocalDate asOnDate) {
        BigDecimal remainingReceipt = money(receipts);
        int overdueCount = 0;
        BigDecimal overdueAmount = ZERO;
        LocalDate overdueFrom = null;
        LocalDate overdueEnd = null;
        BigDecimal currentDue = ZERO;
        LocalDate currentDueDate = null;

        for (Installment installment : schedule) {
            BigDecimal unpaid = installment.amount();
            if (remainingReceipt.compareTo(ZERO) > 0) {
                BigDecimal applied = remainingReceipt.min(unpaid);
                unpaid = unpaid.subtract(applied).setScale(2, RoundingMode.HALF_UP);
                remainingReceipt = remainingReceipt.subtract(applied).setScale(2, RoundingMode.HALF_UP);
            }

            if (unpaid.compareTo(ZERO) <= 0) {
                continue;
            }

            if (!installment.dueDate().isAfter(asOnDate)) {
                overdueCount++;
                overdueAmount = overdueAmount.add(unpaid).setScale(2, RoundingMode.HALF_UP);
                if (overdueFrom == null || installment.dueDate().isBefore(overdueFrom)) {
                    overdueFrom = installment.dueDate();
                }
                if (overdueEnd == null || installment.dueDate().isAfter(overdueEnd)) {
                    overdueEnd = installment.dueDate();
                }
            } else if (currentDueDate == null) {
                currentDue = unpaid;
                currentDueDate = installment.dueDate();
            }
        }

        return new Allocation(overdueCount, money(overdueAmount), overdueFrom, overdueEnd, money(currentDue), currentDueDate);
    }

    private List<Installment> schedule(LocalDate firstEmiDate, String frequency, List<DemandListRepaymentSlab> slabs) {
        if (firstEmiDate == null || slabs == null || slabs.isEmpty()) {
            return List.of();
        }

        int months = frequencyMonths(frequency);
        LocalDate dueDate = firstEmiDate;
        List<Installment> installments = new ArrayList<>();
        List<DemandListRepaymentSlab> sortedSlabs = slabs.stream()
            .sorted(Comparator.comparing((DemandListRepaymentSlab slab) -> slab.sequenceNo() == null ? 0 : slab.sequenceNo()))
            .toList();

        for (DemandListRepaymentSlab slab : sortedSlabs) {
            int count = slab.numberOfInstallments() == null ? 0 : slab.numberOfInstallments();
            BigDecimal amount = money(slab.installmentAmount());
            for (int index = 0; index < count; index++) {
                installments.add(new Installment(dueDate, amount));
                dueDate = dueDate.plusMonths(months);
            }
        }

        return installments;
    }

    private String warning(DemandListSourceRow source, List<DemandListRepaymentSlab> slabs, BigDecimal totalContractValue) {
        List<String> warnings = new ArrayList<>();
        if (totalContractValue.compareTo(ZERO) <= 0) {
            warnings.add("Total contract value missing or zero");
        }
        if (source.firstEmiDate() == null) {
            warnings.add("EMI start date missing");
        }
        if (slabs == null || slabs.isEmpty()) {
            warnings.add("Repayment structure missing");
        }
        return warnings.isEmpty() ? null : String.join("; ", warnings);
    }

    private BigDecimal proportional(BigDecimal totalOutstanding, BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return totalOutstanding.multiply(numerator)
            .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private int frequencyMonths(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.contains("quarter")) return 3;
        if (normalized.contains("half") || normalized.contains("semi")) return 6;
        if (normalized.contains("year") || normalized.contains("annual")) return 12;
        return 1;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record Installment(LocalDate dueDate, BigDecimal amount) {
    }

    private record Allocation(
        Integer overdueInstallmentCount,
        BigDecimal overdueAmount,
        LocalDate overdueFromDate,
        LocalDate overdueEndDate,
        BigDecimal currentDueAmount,
        LocalDate currentDueDate
    ) {
    }
}
