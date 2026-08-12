package dugar_lms_api.modules.reports.afc;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AfcReportRepositoryTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final AfcReportRepository repository = new AfcReportRepository(jdbcTemplate);

    @Test
    void receiptSqlUsesOnlyAuthorisedReceiptsWithLedgersAndAsOnDate() {
        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        repository.findReceipts(new AfcReportSource(1L, "L1", "C", "P", null, null, null, null, null), LocalDate.of(2026, 8, 6));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));

        assertThat(sql.getValue()).contains("UPPER(TRIM(COALESCE(vh.status, ''))) = 'AUTHORISED'");
        assertThat(sql.getValue()).contains("vh.voucher_date <= :asOnDate");
        assertThat(sql.getValue()).contains("TRIM(COALESCE(vd.ledger_code, '')) IN ('3001', '4201')");
        assertThat(sql.getValue()).contains("UPPER(TRIM(COALESCE(vd.voucher_type, vh.voucher_type, ''))) <> 'HJ'");
        assertThat(sql.getValue()).contains("vh.temporary_receipt_number");
        assertThat(sql.getValue()).contains("vd.sub_ledger_code");
        assertThat(sql.getValue()).doesNotContain("voucher_header_history");
        assertThat(sql.getValue()).doesNotContain("voucher_detail_history");
    }
}
