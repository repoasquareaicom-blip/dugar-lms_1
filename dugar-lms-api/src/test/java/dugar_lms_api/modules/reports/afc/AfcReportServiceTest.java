package dugar_lms_api.modules.reports.afc;

import dugar_lms_api.modules.reports.ReportAccessScope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AfcReportServiceTest {

    @Test
    void validatesLoanNumberAndAsOnDate() {
        AfcReportService service = service(source(), List.of(), List.of());

        assertThatThrownBy(() -> service.getReport(new AfcReportRequest("", LocalDate.now(), null), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Loan Number is required");
        assertThatThrownBy(() -> service.getReport(new AfcReportRequest("L1", null, null), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("As On Date is required");
    }

    @Test
    void reportReturnsHeaderAndRowsForPrintEndpointShape() {
        AfcReportService service = service(source(), List.of(new AfcRepaymentSlab(1L, 1, 1, new BigDecimal("400"))), List.of());

        AfcReportResponse response = service.getReport(new AfcReportRequest("L1", LocalDate.of(2026, 1, 1), null), null);

        assertThat(response.header().loanNumber()).isEqualTo("L1");
        assertThat(response.rows()).hasSize(1);
    }

    private AfcReportService service(AfcReportSource source, List<AfcRepaymentSlab> slabs, List<AfcReceipt> receipts) {
        return new AfcReportService(new StubRepository(source, slabs, receipts), new AfcCalculationService());
    }

    private AfcReportSource source() {
        return new AfcReportSource(1L, "L1", "Customer", "Vehicles", new BigDecimal("600"), new BigDecimal("200"), new BigDecimal("800"), LocalDate.of(2026, 1, 1), "Monthly");
    }

    private static class StubRepository extends AfcReportRepository {
        private final AfcReportSource source;
        private final List<AfcRepaymentSlab> slabs;
        private final List<AfcReceipt> receipts;

        StubRepository(AfcReportSource source, List<AfcRepaymentSlab> slabs, List<AfcReceipt> receipts) {
            super(null);
            this.source = source;
            this.slabs = slabs;
            this.receipts = receipts;
        }

        @Override
        public Optional<AfcReportSource> findSource(String loanNumber, String areaCode) {
            return Optional.ofNullable(source);
        }

        @Override
        public Optional<AfcReportSource> findSource(String loanNumber, String areaCode, ReportAccessScope accessScope) {
            return Optional.ofNullable(source);
        }

        @Override
        public List<AfcRepaymentSlab> findRepaymentSlabs(Long contractId) {
            return slabs;
        }

        @Override
        public List<AfcReceipt> findReceipts(AfcReportSource source, LocalDate asOnDate) {
            return receipts;
        }
    }
}
