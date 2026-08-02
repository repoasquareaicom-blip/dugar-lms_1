package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;

public record ContractDashboardTotalsDto(
    Long totalActiveContracts,
    BigDecimal totalAum
) {
}
