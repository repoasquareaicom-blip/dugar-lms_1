package dugar_lms_api.modules.contracts.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractListCriteria(
    String keyword,
    String branch,
    String status,
    String product,
    String customerName,
    LocalDate contractDateFrom,
    LocalDate contractDateTo,
    BigDecimal minimumLoanAmount,
    BigDecimal maximumLoanAmount,
    Boolean isDraft,
    String workflowStatus,
    Integer page,
    Integer size,
    String sortColumn,
    String sortDirection
) {
}
