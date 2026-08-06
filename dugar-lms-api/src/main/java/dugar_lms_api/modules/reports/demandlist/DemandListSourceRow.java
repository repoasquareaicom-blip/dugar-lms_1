package dugar_lms_api.modules.reports.demandlist;

import java.math.BigDecimal;
import java.time.LocalDate;

record DemandListSourceRow(
    Long contractId,
    String loanNumber,
    String borrowerName,
    String guarantorName,
    String productType,
    String contractType,
    String assetDescription,
    String vehicleTypeCode,
    String registrationOrLocation,
    String ownerNumber,
    String usage,
    BigDecimal loanAmount,
    BigDecimal financeCharges,
    BigDecimal totalContractValue,
    LocalDate firstEmiDate,
    String paymentFrequency,
    String area,
    String fieldOfficer,
    BigDecimal authorisedReceipts
) {
}
