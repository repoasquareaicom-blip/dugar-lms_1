package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.service.ContractListCriteria;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractListRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Test
    void generatedSqlUsesWhitelistedSortAndStableSecondarySort() {
        ContractListRepository repository = new ContractListRepository(namedParameterJdbcTemplate);
        when(namedParameterJdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        repository.find(new ContractListCriteria(
            "14507",
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
            0,
            25,
            "loanAmount",
            "asc"
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).query(
            sqlCaptor.capture(),
            paramsCaptor.capture(),
            any(RowMapper.class)
        );

        assertThat(sqlCaptor.getValue()).contains("ORDER BY c.loan_amount ASC, c.contract_id DESC");
        assertThat(sqlCaptor.getValue()).doesNotContain("14507");
        assertThat(((MapSqlParameterSource) paramsCaptor.getValue()).getValue("keywordPattern")).isEqualTo("%14507%");
    }

    @Test
    void contractIdPrimarySortDoesNotDuplicateSecondarySort() {
        ContractListRepository repository = new ContractListRepository(namedParameterJdbcTemplate);
        when(namedParameterJdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        repository.find(new ContractListCriteria(
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
            0,
            25,
            "contractId",
            "desc"
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).query(
            sqlCaptor.capture(),
            any(SqlParameterSource.class),
            any(RowMapper.class)
        );

        assertThat(sqlCaptor.getValue()).contains("ORDER BY c.contract_id DESC");
        assertThat(sqlCaptor.getValue()).doesNotContain("c.contract_id DESC, c.contract_id DESC");
    }

    @Test
    void countQueryUsesKeywordParameters() {
        ContractListRepository repository = new ContractListRepository(namedParameterJdbcTemplate);
        when(namedParameterJdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Long.class)))
            .thenReturn(0L);

        repository.count(new ContractListCriteria(
            "MH01AB1234",
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
            0,
            25,
            "contractId",
            "desc"
        ));

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).queryForObject(
            any(String.class),
            paramsCaptor.capture(),
            eq(Long.class)
        );

        MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
        assertThat(params.getValue("keywordPattern")).isEqualTo("%mh01ab1234%");
    }

    @Test
    void suppliedFiltersAreAddedWithNamedParameters() {
        ContractListRepository repository = new ContractListRepository(namedParameterJdbcTemplate);
        when(namedParameterJdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        repository.find(new ContractListCriteria(
            null,
            "BR-01",
            "ACTIVE",
            "HP",
            "Ravi",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            new BigDecimal("100000"),
            new BigDecimal("500000"),
            null,
            null,
            1,
            25,
            "createdDate",
            "desc"
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).query(
            sqlCaptor.capture(),
            paramsCaptor.capture(),
            any(RowMapper.class)
        );

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("c.area_code = :branch");
        assertThat(sql).contains("UPPER(TRIM(COALESCE(c.status, ''))) = :status");
        assertThat(sql).contains("c.contract_type = :product");
        assertThat(sql).contains("LOWER(COALESCE(pm.full_name, '')) LIKE :customerNamePattern");
        assertThat(sql).contains("c.contract_date >= :contractDateFrom");
        assertThat(sql).contains("c.contract_date <= :contractDateTo");
        assertThat(sql).contains("c.loan_amount >= :minimumLoanAmount");
        assertThat(sql).contains("c.loan_amount <= :maximumLoanAmount");

        MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
        assertThat(params.getValue("branch")).isEqualTo("BR-01");
        assertThat(params.getValue("status")).isEqualTo("Y");
        assertThat(params.getValue("product")).isEqualTo("HP");
        assertThat(params.getValue("customerNamePattern")).isEqualTo("%ravi%");
        assertThat(params.getValue("contractDateFrom")).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(params.getValue("contractDateTo")).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(params.getValue("minimumLoanAmount")).isEqualTo(new BigDecimal("100000"));
        assertThat(params.getValue("maximumLoanAmount")).isEqualTo(new BigDecimal("500000"));
        assertThat(params.getValue("offset")).isEqualTo(25L);
    }

    @Test
    void loanReferenceOptionsUseContractNumberPrefixBeforeLimit() {
        ContractListRepository repository = new ContractListRepository(namedParameterJdbcTemplate);
        when(namedParameterJdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        repository.findLoanReferenceOptions("14675", 10, new ReportAccessScope(false));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).query(
            sqlCaptor.capture(),
            paramsCaptor.capture(),
            any(RowMapper.class)
        );

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("LOWER(TRIM(c.contract_number)) LIKE :contractPrefix");
        assertThat(sql).contains("AND c.loan_close_date IS NULL");
        assertThat(sql).contains("WHERE c.is_active = TRUE");
        assertThat(sql).contains("AND UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'");
        assertThat(sql).contains("ORDER BY TRIM(c.contract_number)");
        assertThat(sql).contains("LIMIT :limit");
        assertThat(sql).doesNotContain("LOWER(COALESCE(c.borrower_code, '')) LIKE");

        MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
        assertThat(params.getValue("contractPrefix")).isEqualTo("14675%");
        assertThat(params.getValue("limit")).isEqualTo(10);
    }

    @Test
    void loanReferenceOptionsPreserveAccessScope() {
        ContractListRepository repository = new ContractListRepository(namedParameterJdbcTemplate);
        when(namedParameterJdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        repository.findLoanReferenceOptions("14", 75, new ReportAccessScope(false, 42L, "user"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).query(
            sqlCaptor.capture(),
            paramsCaptor.capture(),
            any(RowMapper.class)
        );

        assertThat(sqlCaptor.getValue()).contains("scope_area.area_code");
        assertThat(sqlCaptor.getValue()).contains("scope_contract.contract_number");

        MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
        assertThat(params.getValue("fullAccess")).isEqualTo(false);
        assertThat(params.getValue("scopeUserId")).isEqualTo(42L);
        assertThat(params.getValue("limit")).isEqualTo(50);
    }

    @Test
    void restrictedAccessUsesUpdatedByUserGroupOnly() {
        ContractListRepository repository = new ContractListRepository(namedParameterJdbcTemplate);
        when(namedParameterJdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        repository.find(new ContractListCriteria(
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
            0,
            25,
            "contractId",
            "desc"
        ), new ReportAccessScope(true));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).query(
            sqlCaptor.capture(),
            paramsCaptor.capture(),
            any(RowMapper.class)
        );

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("access_user.user_id::text = TRIM(COALESCE(c.updated_by, ''))");
        assertThat(sql).contains("LOWER(TRIM(COALESCE(access_user.user_group, ''))) = 'user'");
        assertThat(sql).doesNotContain("created_user");
        assertThat(sql).doesNotContain("c.created_by");
        assertThat(((MapSqlParameterSource) paramsCaptor.getValue()).getValue("fullAccess")).isEqualTo(false);
    }

    @Test
    void cibilSubmissionExportUsesActiveContractFiltersAndDoesNotApplyPagination() {
        ContractListRepository repository = new ContractListRepository(namedParameterJdbcTemplate);
        ContractListCriteria criteria = new ContractListCriteria(
            "Ravi",
            "BR-01",
            null,
            "HP",
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            3,
            25,
            "contractDate",
            "desc"
        );

        String sql = repository.cibilSubmissionExportSql(criteria, new ReportAccessScope(false));
        assertThat(sql).contains("WHERE c.is_active = TRUE");
        assertThat(sql).contains("UPPER(TRIM(COALESCE(c.status, ''))) = 'Y'");
        assertThat(sql).contains("FROM contract_repayment_structures repayment_exists");
        assertThat(sql).contains("LEFT JOIN tmp_contract_report tcr");
        assertThat(sql).contains("c.area_code = :branch");
        assertThat(sql).contains("c.contract_type = :product");
        assertThat(sql).contains("ORDER BY c.contract_date DESC, c.contract_id DESC");
        assertThat(sql).doesNotContain("LIMIT :size");
        assertThat(sql).doesNotContain("OFFSET :offset");

        MapSqlParameterSource params = (MapSqlParameterSource) repository.cibilSubmissionExportParams(criteria, new ReportAccessScope(false));
        assertThat(params.getValue("keywordPattern")).isEqualTo("%ravi%");
        assertThat(params.getValue("branch")).isEqualTo("BR-01");
        assertThat(params.getValue("product")).isEqualTo("HP");
    }
}
