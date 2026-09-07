package dugar_lms_api.modules.contracts.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContractPartyDraftRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Test
    void insertContractDraftPersistsCreatedByAndUpdatedBy() {
        ContractPartyDraftRepository repository = new ContractPartyDraftRepository(namedParameterJdbcTemplate);

        repository.insertContractDraft(100L, 200L, "C100", LocalDate.of(2026, 8, 31), "B1", null, null, null, "7");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("created_by").contains("updated_by");
        assertThat(paramsCaptor.getValue().getValue("updatedBy")).isEqualTo("7");
    }
}
