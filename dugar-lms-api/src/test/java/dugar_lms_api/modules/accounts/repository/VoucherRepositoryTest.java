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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        assertThat(((MapSqlParameterSource) paramsCaptor.getValue()).getValue("restricted")).isEqualTo(true);
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
            "AUTO",
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
