package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;

public record ContractRepaymentStructureDto(
    Integer sequenceNo,
    Integer numberOfInstallments,
    BigDecimal installmentAmount
) {
}
