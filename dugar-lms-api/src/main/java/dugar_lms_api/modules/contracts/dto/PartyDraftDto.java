package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PartyDraftDto(
    String role,
    String partyCode,
    String customerType,
    String salutation,
    String fullName,
    String firmName,
    String partner1Name,
    String partner2Name,
    String swdName,
    String addressLine1,
    String addressLine2,
    String area,
    String city,
    String state,
    String pinCode,
    String contactNumber,
    String alternativeNumber,
    String emailId,
    LocalDate dateOfBirth,
    String panNumber,
    String aadhaarNumber,
    String occupation,
    BigDecimal annualIncome,
    String residenceType,
    BigDecimal distanceKm
) {
}
