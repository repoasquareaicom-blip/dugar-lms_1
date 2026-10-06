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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    public CibilSubmissionExportService(
        ContractListService contractListService,
        ContractListRepository contractListRepository
    ) {
        this.contractListService = contractListService;
        this.contractListRepository = contractListRepository;
    }

    public ExportResult export(ContractListCriteria criteria, Authentication authentication) {
        ContractListCriteria exportCriteria = contractListService.validatedCriteria(criteria);
        ReportAccessScope accessScope = ReportAccessScope.from(authentication);
        List<CibilSubmissionExportRow> rows = contractListRepository.findCibilSubmissionExport(exportCriteria, accessScope);
        return new ExportResult(workbook(rows), rows.size());
    }

    byte[] workbook(List<CibilSubmissionExportRow> rows) {
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
                writeDataRow(sheet.createRow(rowIndex + 1), rows.get(rowIndex));
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

    private void writeDataRow(Row row, CibilSubmissionExportRow source) {
        setText(row, 0, source.consumerName());
        setDate(row, 1, source.dateOfBirth());
        setText(row, 3, source.panNumber());
        setText(row, 12, source.aadhaarNumber());
        setText(row, 15, source.mobileNumber());
        setText(row, 19, source.coApplicantMobileNumber());
        setText(row, 21, source.emailId());
        setText(row, 22, source.emailId());
        setText(row, 23, source.city());
        setText(row, 35, source.contractNumber());
        setDate(row, 38, source.contractDate());
        setNumber(row, 42, source.loanAmount());
        setNumber(row, 60, source.emiAmount());
        setNumber(row, 65, source.emiAmount());
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

    public record ExportResult(byte[] workbook, int recordCount) {
    }
}
