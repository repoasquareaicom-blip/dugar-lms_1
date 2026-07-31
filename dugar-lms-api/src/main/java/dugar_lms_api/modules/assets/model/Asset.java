package dugar_lms_api.modules.assets.model;

import dugar_lms_api.common.model.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class Asset extends BaseEntity {

    private Long assetId;

    private Long contractId;

    private String oracleContractType;

    private String oracleContractNo;

    private String financeType;

    private String vehicleTypeCode;

    private String registrationNumber;

    private String engineNumber;

    private String chassisNumber;

    private String manufactureYear;

    private BigDecimal equipmentValue;

    private String securityOffered;

    private String ownerSerialNo;

    private String sourceTable;

    private String sourceRowHash;
}
