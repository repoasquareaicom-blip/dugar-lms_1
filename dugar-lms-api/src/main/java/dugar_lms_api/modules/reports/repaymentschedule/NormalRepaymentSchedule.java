package dugar_lms_api.modules.reports.repaymentschedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record NormalRepaymentSchedule(Details details, String frequency, List<Installment> installments, BigDecimal total) {
    record Details(String contractNumber, LocalDate contractDate, Integer tenureMonths, LocalDate firstEmiDate,
                   String paymentFrequency, String repaymentTerms, String vehicle, String borrowerName,
                   String relatedPerson, String addressLine1, String addressLine2, String area, String city,
                   String state, String pinCode, BigDecimal financeAmount, BigDecimal financeCharges,
                   BigDecimal bpfc, BigDecimal insuranceDeposit, BigDecimal contractValue) {}

    record Installment(int number, LocalDate dueDate, BigDecimal amount) {}
}
