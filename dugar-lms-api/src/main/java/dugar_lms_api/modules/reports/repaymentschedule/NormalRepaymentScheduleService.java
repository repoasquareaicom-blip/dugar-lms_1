package dugar_lms_api.modules.reports.repaymentschedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static dugar_lms_api.modules.reports.repaymentschedule.NormalRepaymentSchedule.*;

@Service
public class NormalRepaymentScheduleService {
    private final RepaymentScheduleService access;
    private final RepaymentScheduleRepository repository;
    private final TransactionTemplate monthlyLookup;

    public NormalRepaymentScheduleService(RepaymentScheduleService access, RepaymentScheduleRepository repository,
                                          PlatformTransactionManager transactions) {
        this.access = access;
        this.repository = repository;
        this.monthlyLookup = new TransactionTemplate(transactions);
        this.monthlyLookup.setPropagationBehavior(Propagation.NESTED.value());
        this.monthlyLookup.setReadOnly(true);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NormalRepaymentSchedule load(long contractId, Authentication authentication) {
        access.requireContractAccess(contractId, authentication);
        try {
            var details = repository.normalDetails(contractId).orElseThrow(RepaymentScheduleService::inaccessibleContract);
            Frequency frequency = Frequency.from(details.paymentFrequency(), details.repaymentTerms());
            var slabs = repository.slabs(contractId);
            var monthlyRows = frequency == null || frequency == Frequency.MONTHLY
                ? readMonthlySchedule(details.contractNumber()) : List.<RepaymentScheduleData.Row>of();
            return build(details, slabs, frequency, monthlyRows);
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Repayment schedule is temporarily unavailable.", exception);
        }
    }

    private List<RepaymentScheduleData.Row> readMonthlySchedule(String contractNumber) {
        try {
            // An unsupported contract rolls back to a savepoint before using its stored contractual slabs.
            return monthlyLookup.execute(status -> repository.schedule(contractNumber));
        } catch (DataAccessException exception) {
            if (exception.getMostSpecificCause() instanceof SQLException sql && "22023".equals(sql.getSQLState())) {
                return List.of();
            }
            throw exception;
        }
    }

    static NormalRepaymentSchedule build(Details details, List<RepaymentScheduleData.Slab> slabs) {
        Frequency frequency = Frequency.from(details.paymentFrequency(), details.repaymentTerms());
        return build(details, slabs, frequency, List.of());
    }

    private static NormalRepaymentSchedule build(Details details, List<RepaymentScheduleData.Slab> slabs,
                                                 Frequency frequency, List<RepaymentScheduleData.Row> monthlyRows) {
        boolean fromFunction = !monthlyRows.isEmpty();
        if (frequency == null && !fromFunction) {
            throw invalid("Repayment frequency is missing from the contract.");
        }
        if (details.firstEmiDate() == null || details.contractValue() == null || slabs.isEmpty()) {
            throw invalid("The first EMI date, repayment total or repayment structure is missing.");
        }

        // Keep the stored slab precision for reconciliation even when the function supplies the displayed rows.
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal contractualTotal = BigDecimal.ZERO;
        for (var slab : slabs) {
            if (slab.installments() <= 0 || slab.amount() == null || slab.amount().signum() <= 0) {
                throw invalid("The contract contains an invalid repayment slab.");
            }
            contractualTotal = contractualTotal.add(slab.amount().multiply(BigDecimal.valueOf(slab.installments())));
            for (int n = 0; n < slab.installments(); n++) amounts.add(slab.amount());
        }
        if (contractualTotal.compareTo(details.contractValue()) != 0 || (fromFunction && monthlyRows.size() != amounts.size())) {
            throw invalid("The instalment amounts do not reconcile with the contractual repayment total.");
        }
        if (frequency == Frequency.BULLET && amounts.size() != 1) {
            throw invalid("Multiple bullet instalments require individual contractual due dates.");
        }

        BigDecimal total = wholeRupees(contractualTotal);
        List<BigDecimal> rounded = new ArrayList<>(fromFunction
            ? monthlyRows.stream().map(RepaymentScheduleData.Row::emiAmount).map(NormalRepaymentScheduleService::wholeRupees).toList()
            : amounts.stream().map(NormalRepaymentScheduleService::wholeRupees).toList());
        reconcileRounding(amounts, rounded, total);
        List<Installment> installments = IntStream.range(0, rounded.size())
            .mapToObj(i -> new Installment(i + 1,
                fromFunction ? monthlyRows.get(i).dueDate() : frequency.date(details.firstEmiDate(), i), rounded.get(i)))
            .toList();
        return new NormalRepaymentSchedule(details, fromFunction ? Frequency.MONTHLY.label : frequency.label, installments, total);
    }

    private static void reconcileRounding(List<BigDecimal> exact, List<BigDecimal> rounded, BigDecimal total) {
        int difference = total.subtract(rounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).intValueExact();
        if (difference == 0) return;
        int direction = Integer.signum(difference);
        var adjustments = IntStream.range(0, exact.size()).boxed()
            .sorted(Comparator.comparing(i -> rounded.get(i).subtract(exact.get(i)).multiply(BigDecimal.valueOf(direction))))
            .limit(Math.abs(difference)).toList();
        // Move the rows closest to the rounding threshold by one rupee, keeping the displayed total exact.
        for (int index : adjustments) rounded.set(index, rounded.get(index).add(BigDecimal.valueOf(direction)));
    }

    private static BigDecimal wholeRupees(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private enum Frequency {
        WEEKLY("Weekly", ChronoUnit.WEEKS, 1),
        FORTNIGHTLY("Fortnightly", ChronoUnit.WEEKS, 2),
        MONTHLY("Monthly", ChronoUnit.MONTHS, 1),
        QUARTERLY("Quarterly", ChronoUnit.MONTHS, 3),
        HALF_YEARLY("Half-yearly", ChronoUnit.MONTHS, 6),
        YEARLY("Yearly", ChronoUnit.YEARS, 1),
        BULLET("Bullet", ChronoUnit.DAYS, 0);

        private final String label;
        private final ChronoUnit unit;
        private final int step;

        Frequency(String label, ChronoUnit unit, int step) {
            this.label = label;
            this.unit = unit;
            this.step = step;
        }

        LocalDate date(LocalDate first, int index) {
            return first.plus((long) step * index, unit);
        }

        static Frequency from(String paymentFrequency, String repaymentTerms) {
            Frequency payment = parse(paymentFrequency);
            Frequency terms = parse(repaymentTerms);
            if (payment != null && terms != null && payment != terms) {
                throw invalid("The contract has conflicting repayment frequencies.");
            }
            return payment == null ? terms : payment;
        }

        private static Frequency parse(String value) {
            if (value == null || value.isBlank()) return null;
            return switch (value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "")) {
                case "weekly" -> WEEKLY;
                case "fortnightly" -> FORTNIGHTLY;
                case "monthly" -> MONTHLY;
                case "quarterly" -> QUARTERLY;
                case "halfyearly", "semiannual", "semiannually" -> HALF_YEARLY;
                case "annual", "annually", "yearly" -> YEARLY;
                case "bullet" -> BULLET;
                default -> throw invalid("The contract has an unsupported repayment frequency.");
            };
        }
    }
}
