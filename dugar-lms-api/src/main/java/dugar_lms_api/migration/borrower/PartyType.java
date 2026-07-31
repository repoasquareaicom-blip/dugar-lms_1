package dugar_lms_api.migration.borrower;

public enum PartyType {
    BORROWER("H"),
    GUARANTOR("G");

    private final String sourceCode;

    PartyType(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String sourceCode() {
        return sourceCode;
    }

    public static PartyType fromSourceCode(String value) {
        if ("H".equalsIgnoreCase(value)) {
            return BORROWER;
        }
        if ("G".equalsIgnoreCase(value)) {
            return GUARANTOR;
        }
        throw new IllegalArgumentException("Invalid party type: " + value);
    }
}
