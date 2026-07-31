package dugar_lms_api.migration.repaymentstructure;

import java.math.BigDecimal;

record RepaymentStructureRecord(
    Long contractId,
    Integer sequenceNo,
    Integer numberOfInstallments,
    BigDecimal installmentAmount
) {
}
