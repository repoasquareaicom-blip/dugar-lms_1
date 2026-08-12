package dugar_lms_api.modules.reports.demandlist;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemandListRowDto(
    Long contractId,
    String loanNumber,
    String borrowerCode,
    String borrowerName,
    String guarantorCode,
    String guarantorName,
    String productType,
    String assetDescription,
    String vehicleTypeCode,
    String registrationOrLocation,
    String ownerNumber,
    String usage,
    BigDecimal loanAmount,
    BigDecimal flatInterestRate,
    BigDecimal contractValue,
    BigDecimal authorisedReceipts,
    BigDecimal principalOutstanding,
    BigDecimal interestOutstanding,
    BigDecimal totalOutstanding,
    Integer overdueInstallmentCount,
    BigDecimal overdueAmount,
    LocalDate overdueFromDate,
    LocalDate overdueEndDate,
    BigDecimal currentDueAmount,
    LocalDate currentDueDate,
    String area,
    String fieldOfficer,
    String warning
) {
}
