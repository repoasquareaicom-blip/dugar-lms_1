package dugar_lms_api.modules.reports.afc;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AfcReportHeaderDto(
    Long contractId,
    String loanNumber,
    String customerName,
    String productType,
    BigDecimal loanAmount,
    BigDecimal financeCharges,
    BigDecimal contractValue,
    LocalDate emiStartDate,
    LocalDate asOnDate
) {
}
