package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CibilSubmissionExportRow(
    Long contractId,
    String consumerName,
    LocalDate dateOfBirth,
    String salutation,
    String panNumber,
    String aadhaarNumber,
    String mobileNumber,
    String coApplicantMobileNumber,
    String emailId,
    String city,
    String addressLine1,
    String addressLine2,
    String area,
    String state,
    String pinCode,
    String contractNumber,
    String contractType,
    String vehicleTypeCode,
    LocalDate contractDate,
    LocalDate lastPaymentDate,
    BigDecimal loanAmount,
    BigDecimal currentBalance,
    BigDecimal amountOverdue,
    LocalDate overdueFromDate,
    Boolean demandListMatched,
    Integer repaymentTenure,
    BigDecimal emiAmount
) {
}
