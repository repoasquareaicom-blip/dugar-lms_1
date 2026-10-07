package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.CibilSubmissionExportRow;
import dugar_lms_api.modules.contracts.repository.ContractListRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.List;

@Service
public class CibilSubmissionExportService {

    public static final String[] CIBIL_HEADERS = {
        "Consumer Name",
        "Date of Birth",
        "Gender",
        "Income Tax ID Number",
        "Passport Number",
        "Passport Issue Date",
        "Passport Expiry Date",
        "Voter ID Number",
        "Driving License Number",
        "Driving License Issue Date",
        "Driving License Expiry Date",
        "Ration Card Number",
        "Universal ID Number",
        "Additional ID #1",
        "Additional ID #2",
        "Telephone No.Mobile",
        "Telephone No.Residence",
        "Telephone No.Office",
        "Extension Office",
        "Telephone No.Other",
        "Extension Other",
        "Email ID 1",
        "Email ID 2",
        "Address Line 1",
        "State Code 1",
        "PIN Code 1",
        "Address Category 1",
        "Residence Code 1",
        "Address Line 2",
        "State Code 2",
        "PIN Code 2",
        "Address Category 2",
        "Residence Code 2",
        "Current/New Member Code",
        "Current/New Member Short Name",
        "Curr/New Account No",
        "Account Type",
        "Ownership Indicator",
        "Date Opened/Disbursed",
        "Date of Last Payment",
        "Date Closed",
        "Date Reported",
        "High Credit/Sanctioned Amt",
        "Current Balance",
        "Amt Overdue",
        "No of Days Past Due",
        "Old Mbr Code",
        "Old Mbr Short Name",
        "Old Acc No",
        "Old Acc Type",
        "Old Ownership Indicator",
        "Suit Filed / Wilful Default",
        "Credit Facility Status",
        "Asset Classification",
        "Value of Collateral",
        "Type of Collateral",
        "Credit Limit",
        "Cash Limit",
        "Rate of Interest",
        "RepaymentTenure",
        "EMI Amount",
        "Written- off Amount (Total)",
        "Written- off Principal Amount",
        "Settlement Amt",
        "Payment Frequency",
        "Actual Payment Amt",
        "Occupation Code",
        "Income",
        "Net/Gross Income Indicator",
        "Monthly/Annual Income Indicator",
        "CKYC",
        "NREGA Card Number"
    };

    private static final DateTimeFormatter CIBIL_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final ContractListService contractListService;
    private final ContractListRepository contractListRepository;
    private final Clock clock;

    @Autowired
    public CibilSubmissionExportService(
        ContractListService contractListService,
        ContractListRepository contractListRepository
    ) {
        this(contractListService, contractListRepository, Clock.systemDefaultZone());
    }

    CibilSubmissionExportService(
        ContractListService contractListService,
        ContractListRepository contractListRepository,
        Clock clock
    ) {
        this.contractListService = contractListService;
        this.contractListRepository = contractListRepository;
        this.clock = clock;
    }

    public ExportResult export(ContractListCriteria criteria, Authentication authentication) {
        ContractListCriteria exportCriteria = contractListService.validatedCriteria(criteria);
        ReportAccessScope accessScope = ReportAccessScope.from(authentication);
        LocalDate asOnDate = LocalDate.now(clock);
        List<CibilSubmissionExportRow> rows = contractListRepository.findCibilSubmissionExport(exportCriteria, accessScope, asOnDate);
        return new ExportResult(workbook(rows, asOnDate), rows.size());
    }

    byte[] workbook(List<CibilSubmissionExportRow> rows) {
        return workbook(rows, LocalDate.now(clock));
    }

    byte[] workbook(List<CibilSubmissionExportRow> rows, LocalDate asOnDate) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("CIBIL Submission");
            CellStyle headerStyle = headerStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < CIBIL_HEADERS.length; index++) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(CIBIL_HEADERS[index]);
                cell.setCellStyle(headerStyle);
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                writeDataRow(sheet.createRow(rowIndex + 1), rows.get(rowIndex), asOnDate);
            }

            for (int index = 0; index < CIBIL_HEADERS.length; index++) {
                sheet.autoSizeColumn(index);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create CIBIL Submission export", exception);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private void writeDataRow(Row row, CibilSubmissionExportRow source, LocalDate asOnDate) {
        setText(row, 0, source.consumerName());
        setDate(row, 1, source.dateOfBirth());
        setText(row, 2, gender(source.salutation()));
        setText(row, 3, source.panNumber());
        setText(row, 12, source.aadhaarNumber());
        setText(row, 15, source.mobileNumber());
        setText(row, 19, source.coApplicantMobileNumber());
        setText(row, 21, source.emailId());
        setText(row, 22, source.emailId());
        setText(row, 23, residentialAddressLine1(source));
        setText(row, 24, source.state());
        setText(row, 25, source.pinCode());
        setText(row, 26, "1");
        setText(row, 35, source.contractNumber());
        setText(row, 36, accountType(source));
        setText(row, 37, "1");
        setDate(row, 38, source.contractDate());
        setDate(row, 39, source.lastPaymentDate());
        setDate(row, 41, asOnDate);
        setNumber(row, 42, source.loanAmount());
        setNumber(row, 43, source.currentBalance());
        setNumber(row, 44, source.amountOverdue());
        Integer daysPastDue = daysPastDue(source, asOnDate);
        setInteger(row, 45, cibilDaysPastDue(daysPastDue));
        setText(row, 53, assetClassification(daysPastDue));
        setInteger(row, 59, source.repaymentTenure());
        setNumber(row, 60, source.emiAmount());
    }

    private void setText(Row row, int columnIndex, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        row.createCell(columnIndex).setCellValue(value.trim());
    }

    private void setDate(Row row, int columnIndex, LocalDate value) {
        if (value == null) {
            return;
        }
        row.createCell(columnIndex).setCellValue(CIBIL_DATE_FORMAT.format(value));
    }

    private void setNumber(Row row, int columnIndex, BigDecimal value) {
        if (value == null) {
            return;
        }
        row.createCell(columnIndex).setCellValue(value.doubleValue());
    }

    private void setInteger(Row row, int columnIndex, Integer value) {
        if (value == null) {
            return;
        }
        row.createCell(columnIndex).setCellValue(value);
    }

    private String accountType(CibilSubmissionExportRow source) {
        String vehicleType = normalize(source.vehicleTypeCode());
        if (vehicleType != null) {
            String vehicleMapped = switch (vehicleType) {
                case "PRIVATE CAR", "TAXI PERMIT" -> "01";
                case "THREE WHEELER" -> "13";
                case "SCV", "MCV", "HCV" -> "17";
                default -> null;
            };
            if (vehicleMapped != null) {
                return vehicleMapped;
            }
        }

        String contractType = normalize(source.contractType());
        if ("LAP".equals(contractType) || "COLLATERAL".equals(contractType)) {
            return "03";
        }
        return null;
    }

    private String residentialAddressLine1(CibilSubmissionExportRow source) {
        return joinSpace(source.addressLine1(), source.addressLine2(), source.area());
    }

    private String gender(String salutation) {
        String normalized = normalize(salutation);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.endsWith(".") ? normalized.substring(0, normalized.length() - 1) : normalized;
        return switch (normalized) {
            case "MR" -> "Male";
            case "MS", "MRS" -> "Female";
            default -> null;
        };
    }

    private String joinSpace(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String cleaned = value == null ? null : value.trim();
            if (cleaned == null || cleaned.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(cleaned);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private Integer daysPastDue(CibilSubmissionExportRow source, LocalDate asOnDate) {
        if (!Boolean.TRUE.equals(source.demandListMatched()) || asOnDate == null) {
            return null;
        }
        if (source.overdueFromDate() == null) {
            return 0;
        }
        return (int) Math.max(ChronoUnit.DAYS.between(source.overdueFromDate(), asOnDate), 0);
    }

    private Integer cibilDaysPastDue(Integer daysPastDue) {
        if (daysPastDue == null) {
            return null;
        }
        return Math.min(daysPastDue, 900);
    }

    private String assetClassification(Integer daysPastDue) {
        if (daysPastDue == null) {
            return null;
        }
        if (daysPastDue == 0) {
            return "01";
        }
        if (daysPastDue <= 90) {
            return "05";
        }
        return "02";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record ExportResult(byte[] workbook, int recordCount) {
    }
}
