package dugar_lms_api.modules.accounts.repository;

import dugar_lms_api.modules.accounts.dto.VoucherAuthorisationCriteria;
import dugar_lms_api.modules.accounts.dto.VoucherDetailRequest;
import dugar_lms_api.modules.accounts.dto.VoucherSaveRequest;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void savePersistsCreatedByUpdatedByAndSubmittedByFromAuthenticatedUser() {
        VoucherRepository repository = new VoucherRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Long.class))).thenReturn(100L);

        repository.save(request(), 7L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(sqlCaptor.capture(), paramsCaptor.capture());

        String headerSql = sqlCaptor.getAllValues().get(0);
        assertThat(headerSql).contains("created_by").contains("updated_by").contains("submitted_by");
        assertThat(paramsCaptor.getAllValues().get(0).getValue("userId")).isEqualTo(7L);
    }

    @Test
    void saveUsesHeaderIdSequenceAndRequestVoucherNumber() {
        VoucherRepository repository = new VoucherRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Long.class)))
            .thenReturn(186355L);

        var response = repository.save(request(), 7L);

        ArgumentCaptor<String> sequenceSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
            sequenceSqlCaptor.capture(),
            any(SqlParameterSource.class),
            eq(Long.class)
        );

        assertThat(sequenceSqlCaptor.getAllValues()).containsExactly(
            "SELECT nextval('voucher_headers_voucher_header_id_seq')"
        );

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(any(String.class), paramsCaptor.capture());
        SqlParameterSource headerParams = paramsCaptor.getAllValues().get(0);
        assertThat(headerParams.getValue("headerId")).isEqualTo(186355L);
        assertThat(headerParams.getValue("voucherNumber")).isEqualTo("123456");
        assertThat(headerParams.getValue("voucherType")).isEqualTo("CP");
        assertThat((String) headerParams.getValue("voucherNumber")).doesNotContain("-");
        assertThat((String) headerParams.getValue("voucherNumber")).doesNotStartWith("CP-");
        assertThat(response.voucherHeaderId()).isEqualTo(186355L);
        assertThat(response.voucherType()).isEqualTo("CP");
        assertThat(response.voucherNumber()).isEqualTo("123456");
    }

    @Test
    void updateDoesNotGenerateAnotherVoucherNumber() throws Exception {
        VoucherRepository repository = new VoucherRepository(jdbcTemplate);
        ResultSet statusResult = org.mockito.Mockito.mock(ResultSet.class);
        when(statusResult.getString("status")).thenReturn("SUBMITTED");
        when(statusResult.getObject("version_number", Integer.class)).thenReturn(1);

        ResultSet headerResult = org.mockito.Mockito.mock(ResultSet.class);
        when(headerResult.getLong("voucher_header_id")).thenReturn(186355L);
        when(headerResult.getString("voucher_type")).thenReturn("CP");
        when(headerResult.getString("voucher_type_description")).thenReturn(null);
        when(headerResult.getString("voucher_number")).thenReturn("123456");
        when(headerResult.getObject("voucher_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 8, 31));
        when(headerResult.getObject("system_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 8, 31));
        when(headerResult.getString("transaction_type")).thenReturn(null);
        when(headerResult.getBigDecimal("voucher_amount")).thenReturn(new BigDecimal("100.00"));
        when(headerResult.getString("contract_number")).thenReturn(null);
        when(headerResult.getObject("contract_id", Long.class)).thenReturn(null);
        when(headerResult.getString("header_control_code")).thenReturn("BANK");
        when(headerResult.getString("header_control_name")).thenReturn("Bank");
        when(headerResult.getString("remarks")).thenReturn(null);
        when(headerResult.getString("status")).thenReturn("SUBMITTED");
        when(headerResult.getObject("version_number", Integer.class)).thenReturn(1);

        AtomicInteger rowMapperCalls = new AtomicInteger();
        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(2);
                return mapper.mapRow(rowMapperCalls.getAndIncrement() == 0 ? statusResult : headerResult, 0);
            });
        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(String.class))).thenReturn("{}");
        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        var response = repository.update(186355L, request(), 7L, new ReportAccessScope(false));

        assertThat(response.voucherHeaderId()).isEqualTo(186355L);
        assertThat(response.voucherNumber()).isEqualTo("123456");
        verify(jdbcTemplate, never()).queryForObject(
            eq("SELECT nextval('public.voucher_number_seq')"),
            any(SqlParameterSource.class),
            eq(Long.class)
        );
        verify(jdbcTemplate, never()).queryForObject(
            eq("SELECT nextval('voucher_headers_voucher_header_id_seq')"),
            any(SqlParameterSource.class),
            eq(Long.class)
        );
    }

    @Test
    void restrictedSearchUsesVoucherUpdatedByUserGroupOnly() {
        VoucherRepository repository = new VoucherRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        repository.search(null, null, null, null, null, new ReportAccessScope(true));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("access_user.user_id = h.updated_by");
        assertThat(sql).contains("LOWER(TRIM(COALESCE(access_user.user_group, ''))) = 'user'");
        assertThat(sql).doesNotContain("h.created_by");
        assertThat(((MapSqlParameterSource) paramsCaptor.getValue()).getValue("fullAccess")).isEqualTo(false);
    }

    @Test
    void restrictedAuthorisationQueueUsesVoucherUpdatedByUserGroupOnly() {
        VoucherRepository repository = new VoucherRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        repository.authorisationQueue(
            new VoucherAuthorisationCriteria(null, null, null, null, null, null, null, 0, 25, null, null),
            new ReportAccessScope(true)
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue()).contains("access_user.user_id = h.updated_by");
        assertThat(sqlCaptor.getValue()).doesNotContain("h.created_by");
    }

    private VoucherSaveRequest request() {
        return new VoucherSaveRequest(
            "PAYMENT",
            "PAYMENT",
            "123456",
            LocalDate.of(2026, 8, 31),
            LocalDate.of(2026, 8, 31),
            "CASH",
            new BigDecimal("100.00"),
            "GENERAL",
            null,
            null,
            null,
            null,
            "BANK",
            "Bank",
            null,
            false,
            List.of(new VoucherDetailRequest(1, "GENERAL", "EXP", "Expense", null, new BigDecimal("100.00"), BigDecimal.ZERO, null, null, null, "Test", null))
        );
    }
}
