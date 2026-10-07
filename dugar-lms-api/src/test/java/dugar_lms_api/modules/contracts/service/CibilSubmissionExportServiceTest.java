package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.CibilSubmissionExportRow;
import dugar_lms_api.modules.contracts.repository.ContractListRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    void workbookPopulatesConfirmedCibilFieldsAndLeavesUnavailableColumnsBlank() throws Exception {
        CibilSubmissionExportService service = service();

        byte[] workbookBytes = service.workbook(List.of(row()));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            Row data = workbook.getSheetAt(0).getRow(1);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("Ravi Kumar");
            assertThat(data.getCell(1).getStringCellValue()).isEqualTo("15011990");
            assertThat(data.getCell(2).getStringCellValue()).isEqualTo("Male");
            assertThat(data.getCell(3).getStringCellValue()).isEqualTo("ABCDE1234F");
            assertThat(data.getCell(12).getStringCellValue()).isEqualTo("123456789012");
            assertThat(data.getCell(15).getStringCellValue()).isEqualTo("9876543210");
            assertThat(data.getCell(19).getStringCellValue()).isEqualTo("9123456780");
            assertThat(data.getCell(21).getStringCellValue()).isEqualTo("ravi@example.com");
            assertThat(data.getCell(22).getStringCellValue()).isEqualTo("ravi@example.com");
            assertThat(data.getCell(23).getStringCellValue()).isEqualTo("SWAMI KI DHANI MUNDAWAR KARANI KOT");
            assertThat(data.getCell(24).getStringCellValue()).isEqualTo("RJ");
            assertThat(data.getCell(25).getStringCellValue()).isEqualTo("301427");
            assertThat(data.getCell(26).getStringCellValue()).isEqualTo("1");
            assertThat(data.getCell(35).getStringCellValue()).isEqualTo("HP001");
            assertThat(data.getCell(36).getStringCellValue()).isEqualTo("01");
            assertThat(data.getCell(37).getStringCellValue()).isEqualTo("1");
            assertThat(data.getCell(38).getStringCellValue()).isEqualTo("03022026");
            assertThat(data.getCell(39).getStringCellValue()).isEqualTo("11062026");
            assertThat(data.getCell(41).getStringCellValue()).isEqualTo("06102026");
            assertThat(data.getCell(42).getNumericCellValue()).isEqualTo(500000.00);
            assertThat(data.getCell(43).getNumericCellValue()).isEqualTo(262798.00);
            assertThat(data.getCell(44).getNumericCellValue()).isEqualTo(355500.00);
            assertThat(data.getCell(45).getNumericCellValue()).isEqualTo(1.00);
            assertThat(data.getCell(53).getStringCellValue()).isEqualTo("05");
            assertThat(data.getCell(59).getNumericCellValue()).isEqualTo(36.00);
            assertThat(data.getCell(60).getNumericCellValue()).isEqualTo(16000.00);

            assertThat(data.getCell(4)).isNull();
            assertThat(data.getCell(58)).isNull();
            assertThat(data.getCell(64)).isNull();
            assertThat(data.getCell(65)).isNull();
            assertThat(data.getCell(71)).isNull();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "Mr.,Male",
        "Mr,Male",
        "Ms.,Female",
        "Mrs.,Female",
        "'',",
        "Dr.,"
    })
    void workbookMapsGenderOnlyFromPrimaryBorrowerSalutation(String salutation, String expectedGender) throws Exception {
        CibilSubmissionExportService service = service();

        byte[] workbookBytes = service.workbook(List.of(row(salutation, LocalDate.of(2026, 10, 5))));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            Row data = workbook.getSheetAt(0).getRow(1);
            if (expectedGender == null) {
                assertThat(data.getCell(2)).isNull();
            } else {
                assertThat(data.getCell(2).getStringCellValue()).isEqualTo(expectedGender);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
        "899,899",
        "900,900",
        "901,900",
        "1500,900"
    })
    void workbookCapsCibilDaysPastDueAtNineHundred(int actualDaysPastDue, int expectedCibilDaysPastDue) throws Exception {
        CibilSubmissionExportService service = service();
        LocalDate asOnDate = LocalDate.of(2026, 10, 6);

        byte[] workbookBytes = service.workbook(List.of(row("Mr.", asOnDate.minusDays(actualDaysPastDue))), asOnDate);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            Row data = workbook.getSheetAt(0).getRow(1);
            assertThat(data.getCell(45).getNumericCellValue()).isEqualTo((double) expectedCibilDaysPastDue);
            assertThat(data.getCell(53).getStringCellValue()).isEqualTo("02");
        }
    }

    @Test
    void workbookLeavesLastPaymentBlankWhenDemandListDateIsNull() throws Exception {
        CibilSubmissionExportService service = service();

        byte[] workbookBytes = service.workbook(List.of(new CibilSubmissionExportRow(
            2L,
            "No Receipt",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "16993",
            "LAP",
            null,
            LocalDate.of(2026, 1, 1),
            null,
            new BigDecimal("1475000.00"),
            new BigDecimal("1475000.00"),
            new BigDecimal("47275.00"),
            LocalDate.of(2026, 10, 5),
            true,
            24,
            null
        )));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            Row data = workbook.getSheetAt(0).getRow(1);
            assertThat(data.getCell(39)).isNull();
            assertThat(data.getCell(43).getNumericCellValue()).isEqualTo(1475000.00);
            assertThat(data.getCell(44).getNumericCellValue()).isEqualTo(47275.00);
            assertThat(data.getCell(45).getNumericCellValue()).isEqualTo(1.00);
            assertThat(data.getCell(53).getStringCellValue()).isEqualTo("05");
        }
    }

    private CibilSubmissionExportService service() {
        return new CibilSubmissionExportService(
            mock(ContractListService.class),
            mock(ContractListRepository.class),
            Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private CibilSubmissionExportRow row() {
        return row("Mr.", LocalDate.of(2026, 10, 5));
    }

    private CibilSubmissionExportRow row(String salutation, LocalDate overdueFromDate) {
        return new CibilSubmissionExportRow(
            1L,
            "Ravi Kumar",
            LocalDate.of(1990, 1, 15),
            salutation,
            "ABCDE1234F",
            "123456789012",
            "9876543210",
            "9123456780",
            "ravi@example.com",
            "Chennai",
            "",
            "SWAMI KI DHANI MUNDAWAR",
            "KARANI KOT",
            "RJ",
            "301427",
            "HP001",
            "HP",
            "Private Car",
            LocalDate.of(2026, 2, 3),
            LocalDate.of(2026, 6, 11),
            new BigDecimal("500000.00"),
            new BigDecimal("262798.00"),
            new BigDecimal("355500.00"),
            overdueFromDate,
            true,
            36,
            new BigDecimal("16000.00")
        );
    }
}
