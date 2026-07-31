package dugar_lms_api.modules.contracts.service;

import java.util.Arrays;

public enum ContractListSortField {
    CONTRACT_ID("contractId", "c.contract_id"),
    CONTRACT_NUMBER("contractNumber", "c.contract_number"),
    LEGACY_CONTRACT_NUMBER("legacyContractNumber", "c.legacy_contract_number"),
    PRODUCT("product", "c.contract_type"),
    BRANCH("branch", "c.area_code"),
    CONTRACT_DATE("contractDate", "c.contract_date"),
    LOAN_AMOUNT("loanAmount", "c.loan_amount"),
    TOTAL_CONTRACT_VALUE("totalContractValue", "c.total_contract_value"),
    TENURE_MONTHS("tenureMonths", "c.tenure_months"),
    IRR_RATE("irrRate", "c.irr_rate"),
    FIRST_EMI_DATE("firstEmiDate", "c.first_emi_date"),
    VEHICLE_REGISTRATION_NUMBER("vehicleRegistrationNumber", "COALESCE(a.registration_number, c.registration_number)"),
    VEHICLE_MAKE("vehicleMake", "c.vehicle_make"),
    VEHICLE_TYPE_CODE("vehicleTypeCode", "a.vehicle_type_code"),
    ENGINE_NUMBER("engineNumber", "COALESCE(a.engine_number, cd.engine_number)"),
    CHASSIS_NUMBER("chassisNumber", "COALESCE(a.chassis_number, cd.chassis_number)"),
    MANUFACTURE_YEAR("manufactureYear", "a.manufacture_year"),
    VEHICLE_FINANCE_TYPE("vehicleFinanceType", "a.finance_type"),
    VEHICLE_VALUE("vehicleValue", "a.equipment_value"),
    VEHICLE_OWNER_SERIAL_NO("vehicleOwnerSerialNo", "COALESCE(a.owner_serial_no, cd.owner_serial_number)"),
    VEHICLE_REGISTRATION_DATE("vehicleRegistrationDate", "cd.registration_date"),
    CUSTOMER_CODE("customerCode", "c.borrower_code"),
    CUSTOMER_NAME("customerName", "pm.full_name"),
    CUSTOMER_MOBILE("customerMobile", "pm.contact_number"),
    CUSTOMER_EMAIL("customerEmail", "pm.email_id"),
    CUSTOMER_ADDRESS("customerAddress", "pm.city"),
    CUSTOMER_CITY("customerCity", "pm.city"),
    CUSTOMER_STATE("customerState", "pm.state"),
    CUSTOMER_PIN_CODE("customerPinCode", "pm.pin_code"),
    CUSTOMER_PAN_NUMBER("customerPanNumber", "pm.pan_number"),
    CUSTOMER_OCCUPATION("customerOccupation", "pm.occupation"),
    GUARANTOR_CODE("guarantorCode", "c.guarantor_code"),
    GUARANTOR_NAME("guarantorName", "gm.full_name"),
    GUARANTOR_MOBILE("guarantorMobile", "gm.contact_number"),
    GUARANTOR_EMAIL("guarantorEmail", "gm.email_id"),
    GUARANTOR_ADDRESS("guarantorAddress", "gm.city"),
    GUARANTOR_CITY("guarantorCity", "gm.city"),
    GUARANTOR_STATE("guarantorState", "gm.state"),
    GUARANTOR_PIN_CODE("guarantorPinCode", "gm.pin_code"),
    GUARANTOR_PAN_NUMBER("guarantorPanNumber", "gm.pan_number"),
    GUARANTOR_OCCUPATION("guarantorOccupation", "gm.occupation");

    private final String apiName;
    private final String sqlColumn;

    ContractListSortField(String apiName, String sqlColumn) {
        this.apiName = apiName;
        this.sqlColumn = sqlColumn;
    }

    public String apiName() {
        return apiName;
    }

    public String sqlColumn() {
        return sqlColumn;
    }

    public static ContractListSortField fromApiName(String apiName) {
        if (apiName == null || apiName.isBlank()) {
            return CONTRACT_DATE;
        }

        String normalized = apiName.trim();
        return Arrays.stream(values())
            .filter(field -> field.apiName.equals(normalized))
            .findFirst()
            .orElse(CONTRACT_DATE);
    }
}
