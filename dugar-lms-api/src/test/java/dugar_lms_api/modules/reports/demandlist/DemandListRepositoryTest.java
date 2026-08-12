package dugar_lms_api.modules.reports.demandlist;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemandListRepositoryTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final DemandListRepository repository = new DemandListRepository(jdbcTemplate);

    @Test
    void receiptSqlIncludesOnlyAuthorisedVouchersLedgers3001And4201AndAsOnDate() {
        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        repository.findSourceRows(new DemandListRequest(LocalDate.of(2026, 8, 6), null, null, null, null, null, null, null, null, null, null, 0, 25, null, null));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), params.capture(), any(RowMapper.class));

        assertThat(sql.getValue()).contains("UPPER(TRIM(COALESCE(vh.status, ''))) = 'AUTHORISED'");
        assertThat(sql.getValue()).contains("vh.voucher_date <= :asOnDate");
        assertThat(sql.getValue()).contains("TRIM(COALESCE(vd.ledger_code, '')) IN ('3001', '4201')");
        assertThat(sql.getValue()).doesNotContain("voucher_header_history");
        assertThat(sql.getValue()).doesNotContain("voucher_detail_history");
        assertThat(((MapSqlParameterSource) params.getValue()).getValue("asOnDate")).isEqualTo(LocalDate.of(2026, 8, 6));
    }

    @Test
    void filtersAndKeywordAreAppliedInRepositorySql() {
        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        repository.findSourceRows(new DemandListRequest(LocalDate.of(2026, 8, 6), "A1", "B1", "FO1", "HP", "Vehicle", null, null, "CN123", null, "ravi", 0, 25, null, null));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), params.capture(), any(RowMapper.class));

        assertThat(sql.getValue()).contains(":areaCode", ":branchId", ":fieldOfficerCode", ":contractType", ":productType", ":contractNumber", ":keyword");
        assertThat(sql.getValue()).contains("UPPER(TRIM(COALESCE(c.area_code, ''))) = :areaCode");
        assertThat(sql.getValue()).doesNotContain("c.area_code, '')) LIKE :areaCode");
        assertThat(sql.getValue()).contains("UPPER(TRIM(COALESCE(c.contract_number, ''))) = :contractNumber");
        assertThat(sql.getValue()).doesNotContain("c.contract_number, '')) LIKE :contractNumber");
        MapSqlParameterSource source = (MapSqlParameterSource) params.getValue();
        assertThat(source.getValue("areaCode")).isEqualTo("A1");
        assertThat(source.getValue("branchId")).isEqualTo("B1");
        assertThat(source.getValue("fieldOfficerCode")).isEqualTo("FO1");
        assertThat(source.getValue("contractType")).isEqualTo("HP");
        assertThat(source.getValue("productType")).isEqualTo("VEHICLE");
        assertThat(source.getValue("contractNumber")).isEqualTo("CN123");
        assertThat(source.getValue("keyword")).isEqualTo("%ravi%");
    }
}
