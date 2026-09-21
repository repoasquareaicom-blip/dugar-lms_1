package dugar_lms_api.modules.reports.demandlist;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemandListRequest(
    LocalDate asOnDate,
    String areaCode,
    String branchId,
    String fieldOfficerCode,
    String contractType,
    String productType,
    BigDecimal minimumOverdueAmount,
    BigDecimal maximumOverdueAmount,
    String contractNumber,
    Integer overdueInstallmentCount,
    String reportType,
    String keyword,
    Integer page,
    Integer size,
    String sortColumn,
    String sortDirection
) {
}
