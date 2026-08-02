package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;
import java.util.List;

public record ContractDashboardDto(
    Long totalActiveContracts,
    BigDecimal totalAum,
    Integer accountYear,
    List<Integer> availableYears,
    List<ContractDashboardBranchDto> branchData,
    List<ContractDashboardDisbursementDto> disbursementData
) {
}
