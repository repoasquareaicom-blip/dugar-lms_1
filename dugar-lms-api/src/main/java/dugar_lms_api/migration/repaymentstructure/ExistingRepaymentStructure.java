package dugar_lms_api.migration.repaymentstructure;

import java.math.BigDecimal;

public record ExistingRepaymentStructure(
    Long repaymentStructureId,
    Long contractId,
    Integer sequenceNo,
    Integer numberOfInstallments,
    BigDecimal installmentAmount
) {
}
