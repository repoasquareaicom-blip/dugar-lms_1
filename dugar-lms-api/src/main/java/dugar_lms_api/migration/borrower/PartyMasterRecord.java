package dugar_lms_api.migration.borrower;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record PartyMasterRecord(
    String partyCode,
    PartyType partyType,
    String salutation,
    String fullName,
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
    String firmName,
    boolean isActive
) {
    public boolean hasSameMappedValues(PartyMasterRecord other) {
        return other != null
            && Objects.equals(partyCode, other.partyCode)
            && partyType == other.partyType
            && Objects.equals(salutation, other.salutation)
            && Objects.equals(fullName, other.fullName)
            && Objects.equals(swdName, other.swdName)
            && Objects.equals(addressLine1, other.addressLine1)
            && Objects.equals(addressLine2, other.addressLine2)
            && Objects.equals(area, other.area)
            && Objects.equals(city, other.city)
            && Objects.equals(state, other.state)
            && Objects.equals(pinCode, other.pinCode)
            && Objects.equals(contactNumber, other.contactNumber)
            && Objects.equals(alternativeNumber, other.alternativeNumber)
            && Objects.equals(emailId, other.emailId)
            && Objects.equals(dateOfBirth, other.dateOfBirth)
            && Objects.equals(panNumber, other.panNumber)
            && Objects.equals(aadhaarNumber, other.aadhaarNumber)
            && Objects.equals(occupation, other.occupation)
            && Objects.equals(annualIncome, other.annualIncome)
            && Objects.equals(firmName, other.firmName)
            && isActive == other.isActive;
    }
}
