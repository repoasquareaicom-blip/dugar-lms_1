package dugar_lms_api.modules.reports.aginganalysis;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AgingAnalysisContractDetailDto(
    Long contractId,
    String contractNumber,
    String areaCode,
    String areaName,
    LocalDate contractDate,
    LocalDate firstEmiDate,
    BigDecimal totalContractValue,
    BigDecimal financeCharges,
    BigDecimal originalPrincipal,
    BigDecimal totalReceived,
    BigDecimal principalRecovered,
    BigDecimal aum,
    BigDecimal totalOutstanding,
    Integer overdueEmiCount,
    String ageingBucket
) {
}
