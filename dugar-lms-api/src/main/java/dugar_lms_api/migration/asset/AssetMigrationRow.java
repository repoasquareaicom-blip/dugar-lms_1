package dugar_lms_api.migration.asset;

import java.math.BigDecimal;

public record AssetMigrationRow(
    int excelRow,
    String contractType,
    String contractNumber,
    String vehicleTypeCode,
    BigDecimal equipmentValue,
    String securityOffered,
    String engineNumber,
    String chassisNumber,
    String ownerSerialNo
) {

    public boolean isBlank() {
        return isBlank(contractType)
            && isBlank(contractNumber)
            && isBlank(vehicleTypeCode)
            && equipmentValue == null
            && isBlank(securityOffered)
            && isBlank(engineNumber)
            && isBlank(chassisNumber)
            && isBlank(ownerSerialNo);
    }

    public boolean isRepeatedHeader() {
        return "CONT_TYPE".equalsIgnoreCase(trim(contractType))
            || "CONT_NO".equalsIgnoreCase(trim(contractNumber));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
