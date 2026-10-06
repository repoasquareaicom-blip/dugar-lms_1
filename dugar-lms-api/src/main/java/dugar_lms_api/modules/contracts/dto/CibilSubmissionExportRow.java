package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CibilSubmissionExportRow(
    String consumerName,
    LocalDate dateOfBirth,
    String panNumber,
    String aadhaarNumber,
    String mobileNumber,
    String coApplicantMobileNumber,
    String emailId,
    String city,
    String contractNumber,
    LocalDate contractDate,
    BigDecimal loanAmount,
    BigDecimal emiAmount
) {
}
