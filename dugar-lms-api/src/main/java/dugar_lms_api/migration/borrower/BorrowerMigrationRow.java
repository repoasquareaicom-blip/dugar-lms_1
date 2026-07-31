package dugar_lms_api.migration.borrower;

public record BorrowerMigrationRow(
    int excelRow,
    String partyCode,
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
    String sourcePartyType
) {
    public boolean isBlank() {
        return isBlank(partyCode)
            && isBlank(salutation)
            && isBlank(fullName)
            && isBlank(swdName)
            && isBlank(addressLine1)
            && isBlank(addressLine2)
            && isBlank(area)
            && isBlank(city)
            && isBlank(state)
            && isBlank(pinCode)
            && isBlank(contactNumber)
            && isBlank(sourcePartyType);
    }

    public boolean isRepeatedHeader() {
        return "PARTY_CODE".equalsIgnoreCase(partyCode)
            || "PARTY_TYPE".equalsIgnoreCase(sourcePartyType);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
