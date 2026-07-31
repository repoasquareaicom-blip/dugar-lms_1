package dugar_lms_api.modules.contracts.service;

public enum ContractListSortDirection {
    ASC("ASC"),
    DESC("DESC");

    private final String sqlKeyword;

    ContractListSortDirection(String sqlKeyword) {
        this.sqlKeyword = sqlKeyword;
    }

    public String sqlKeyword() {
        return sqlKeyword;
    }

    public String apiValue() {
        return sqlKeyword.toLowerCase();
    }

    public static ContractListSortDirection fromApiValue(String sortDirection) {
        if (sortDirection == null || sortDirection.isBlank()) {
            return DESC;
        }

        return switch (sortDirection.trim().toLowerCase()) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> DESC;
        };
    }
}
