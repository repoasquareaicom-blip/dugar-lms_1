package dugar_lms_api.migration.common;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MigrationRunRepositoryTest {

    @Test
    void createRunAcceptsRepaymentStructureAuditMetadataWithoutTruncation() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(2);
            keyHolder.getKeyList().add(Map.of("migration_run_id", 123L));
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class));
        MigrationRunRepository repository = new MigrationRunRepository(jdbcTemplate);

        Long migrationRunId = repository.createRun(
            "REPAYMENT_STRUCTURE",
            "HP_SCHE_DATA_TABLE.xlsx",
            "contract_repayment_structures",
            10,
            "LEGACY_MIGRATION"
        );

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture(), any(KeyHolder.class), any(String[].class));
        MapSqlParameterSource params = paramsCaptor.getValue();

        assertThat(migrationRunId).isEqualTo(123L);
        assertThat(params.getValue("migrationType")).isEqualTo("REPAYMENT_STRUCTURE");
        assertThat(params.getValue("fileName")).isEqualTo("HP_SCHE_DATA_TABLE.xlsx");
        assertThat(params.getValue("sheetName")).isEqualTo("contract_repayment_structures");
    }
}
