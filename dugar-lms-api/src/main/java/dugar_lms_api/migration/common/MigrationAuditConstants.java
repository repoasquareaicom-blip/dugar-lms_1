package dugar_lms_api.migration.common;

public final class MigrationAuditConstants {

    private MigrationAuditConstants() {
    }

    public static final class MigrationTypes {
        public static final String CONTRACT = "CONTRACT";
        public static final String BORROWER_GUARANTOR_MASTER = "BORROWER_GUARANTOR_MASTER";
        public static final String ASSET = "ASSET";
        public static final String ASSET_INSURANCE = "ASSET_INSURANCE";
        public static final String REPAYMENT_STRUCTURE = "REPAYMENT_STRUCTURE";

        private MigrationTypes() {
        }
    }

    public static final class RunStatuses {
        public static final String RUNNING = "RUNNING";
        public static final String COMPLETED = "COMPLETED";
        public static final String PARTIALLY_COMPLETED = "PARTIALLY_COMPLETED";
        public static final String FAILED = "FAILED";

        private RunStatuses() {
        }
    }

    public static final class DetailResultTypes {
        public static final String INSERTED = "INSERTED";
        public static final String DUPLICATE = "DUPLICATE";
        public static final String FAILED = "FAILED";
        public static final String CONFLICT = "CONFLICT";
        public static final String MISSING_REFERENCE = "MISSING_REFERENCE";

        private DetailResultTypes() {
        }
    }
}
