package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;

public record ContractDashboardBranchDto(
    String name,
    Long loans,
    BigDecimal aum
) {
}
