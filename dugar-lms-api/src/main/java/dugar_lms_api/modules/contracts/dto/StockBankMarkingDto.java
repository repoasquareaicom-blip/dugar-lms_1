package dugar_lms_api.modules.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockBankMarkingDto(Long contractId, String contractNumber, LocalDate contractDate,
    String borrowerName, BigDecimal loanAmount, String vehicleMake, String registrationNumber,
    String status, Long bankId, String bankName) {}
