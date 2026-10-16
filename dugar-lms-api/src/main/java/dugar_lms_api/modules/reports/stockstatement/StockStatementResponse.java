package dugar_lms_api.modules.reports.stockstatement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockStatementResponse(Long bankId, String bankCode, String bankName, LocalDate businessDate,
    List<StockStatementRow> rows, int contractCount, BigDecimal totalLoanAmount,
    BigDecimal totalPrincipalPerInstallment, BigDecimal totalPrincipalOutstanding, String fingerprint) {}
