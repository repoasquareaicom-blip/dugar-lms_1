package dugar_lms_api.modules.reports.stockstatement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockStatementRow(Long contractId, String contractNumber, LocalDate contractDate,
    String hirerName, String vehicleDetails, BigDecimal loanAmount, BigDecimal principalPerInstallment,
    int totalEmi, int balanceEmi, BigDecimal principalOutstanding) {}
