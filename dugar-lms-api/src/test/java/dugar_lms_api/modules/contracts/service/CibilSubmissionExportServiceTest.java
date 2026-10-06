package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.CibilSubmissionExportRow;
import dugar_lms_api.modules.contracts.repository.ContractListRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CibilSubmissionExportServiceTest {

    @Test
    void workbookContainsExactlySeventyTwoHeadersInCibilOrder() throws Exception {
        CibilSubmissionExportService service = service();

        byte[] workbookBytes = service.workbook(List.of(row()));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertThat(header.getLastCellNum()).isEqualTo((short) 72);
            for (int index = 0; index < CibilSubmissionExportService.CIBIL_HEADERS.length; index++) {
                assertThat(header.getCell(index).getStringCellValue())
                    .isEqualTo(CibilSubmissionExportService.CIBIL_HEADERS[index]);
            }
        }
    }

    @Test
    void workbookPopulatesOnlyLosMappedFieldsAndLeavesUnavailableColumnsBlank() throws Exception {
        CibilSubmissionExportService service = service();

        byte[] workbookBytes = service.workbook(List.of(row()));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            Row data = workbook.getSheetAt(0).getRow(1);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("Ravi Kumar");
            assertThat(data.getCell(1).getStringCellValue()).isEqualTo("15011990");
            assertThat(data.getCell(3).getStringCellValue()).isEqualTo("ABCDE1234F");
            assertThat(data.getCell(12).getStringCellValue()).isEqualTo("123456789012");
            assertThat(data.getCell(15).getStringCellValue()).isEqualTo("9876543210");
            assertThat(data.getCell(19).getStringCellValue()).isEqualTo("9123456780");
            assertThat(data.getCell(21).getStringCellValue()).isEqualTo("ravi@example.com");
            assertThat(data.getCell(22).getStringCellValue()).isEqualTo("ravi@example.com");
            assertThat(data.getCell(23).getStringCellValue()).isEqualTo("Chennai");
            assertThat(data.getCell(35).getStringCellValue()).isEqualTo("HP001");
            assertThat(data.getCell(38).getStringCellValue()).isEqualTo("03022026");
            assertThat(data.getCell(42).getNumericCellValue()).isEqualTo(500000.00);
            assertThat(data.getCell(60).getNumericCellValue()).isEqualTo(16000.00);
            assertThat(data.getCell(65).getNumericCellValue()).isEqualTo(16000.00);

            assertThat(data.getCell(2)).isNull();
            assertThat(data.getCell(4)).isNull();
            assertThat(data.getCell(24)).isNull();
            assertThat(data.getCell(36)).isNull();
            assertThat(data.getCell(43)).isNull();
            assertThat(data.getCell(71)).isNull();
        }
    }

    private CibilSubmissionExportService service() {
        return new CibilSubmissionExportService(
            mock(ContractListService.class),
            mock(ContractListRepository.class)
        );
    }

    private CibilSubmissionExportRow row() {
        return new CibilSubmissionExportRow(
            "Ravi Kumar",
            LocalDate.of(1990, 1, 15),
            "ABCDE1234F",
            "123456789012",
            "9876543210",
            "9123456780",
            "ravi@example.com",
            "Chennai",
            "HP001",
            LocalDate.of(2026, 2, 3),
            new BigDecimal("500000.00"),
            new BigDecimal("16000.00")
        );
    }
}
