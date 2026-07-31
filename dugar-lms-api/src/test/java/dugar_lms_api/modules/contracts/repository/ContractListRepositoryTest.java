package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.service.ContractListCriteria;
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
        assertThat(sql).contains("c.status = :status");
        assertThat(sql).contains("c.contract_type = :product");
        assertThat(sql).contains("LOWER(COALESCE(pm.full_name, '')) LIKE :customerNamePattern");
        assertThat(sql).contains("c.contract_date >= :contractDateFrom");
        assertThat(sql).contains("c.contract_date <= :contractDateTo");
        assertThat(sql).contains("c.loan_amount >= :minimumLoanAmount");
        assertThat(sql).contains("c.loan_amount <= :maximumLoanAmount");

        MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
        assertThat(params.getValue("branch")).isEqualTo("BR-01");
        assertThat(params.getValue("status")).isEqualTo("ACTIVE");
        assertThat(params.getValue("product")).isEqualTo("HP");
        assertThat(params.getValue("customerNamePattern")).isEqualTo("%ravi%");
        assertThat(params.getValue("contractDateFrom")).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(params.getValue("contractDateTo")).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(params.getValue("minimumLoanAmount")).isEqualTo(new BigDecimal("100000"));
        assertThat(params.getValue("maximumLoanAmount")).isEqualTo(new BigDecimal("500000"));
        assertThat(params.getValue("offset")).isEqualTo(25L);
    }
}
