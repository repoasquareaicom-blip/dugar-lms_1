package dugar_lms_api.modules.reports.afc;

import java.math.BigDecimal;

record AfcRepaymentSlab(
    Long contractId,
    Integer sequenceNo,
    Integer numberOfInstallments,
    BigDecimal installmentAmount
) {
}
