package dugar_lms_api.modules.reports.demandlist;

import java.math.BigDecimal;

record DemandListRepaymentSlab(
    Long contractId,
    Integer sequenceNo,
    Integer numberOfInstallments,
    BigDecimal installmentAmount
) {
}
