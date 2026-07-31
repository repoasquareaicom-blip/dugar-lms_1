package dugar_lms_api.migration.common;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MigrationRunRepository {

    private static final String BASE_RUN_COLUMNS = """
        SELECT
            migration_run_id,
            migration_type,
            file_name,
            sheet_name,
            total_rows,
            inserted_count,
            duplicate_count,
            failed_count,
            status,
            uploaded_by,
            started_at,
            completed_at,
            created_at
        FROM migration_runs
        """;

    private static final String BASE_DETAIL_COLUMNS = """
        SELECT
            migration_run_detail_id,
            migration_run_id,
            excel_row,
            reference_key,
            result_type,
            reason,
            source_data,
            created_at
        FROM migration_run_details
        """;

    private static final String CREATE_RUN_SQL = """
        INSERT INTO migration_runs (
            migration_type,
            file_name,
            sheet_name,
            total_rows,
            inserted_count,
            duplicate_count,
            failed_count,
            status,
            uploaded_by,
            started_at
        )
        VALUES (
            :migrationType,
            :fileName,
            :sheetName,
            :totalRows,
            0,
            0,
            0,
            :status,
            :uploadedBy,
            CURRENT_TIMESTAMP
        )
        """;

    private static final String SAVE_DETAIL_SQL = """
        INSERT INTO migration_run_details (
            migration_run_id,
            excel_row,
            reference_key,
            result_type,
            reason,
            source_data
        )
        VALUES (
            :migrationRunId,
            :excelRow,
            :referenceKey,
            :resultType,
            :reason,
            :sourceData
        )
        """;

    private static final String COMPLETE_RUN_SQL = """
        UPDATE migration_runs
        SET inserted_count = :insertedCount,
            duplicate_count = :duplicateCount,
            failed_count = :failedCount,
            status = :status,
            completed_at = CURRENT_TIMESTAMP
        WHERE migration_run_id = :migrationRunId
        """;

    private static final String FIND_RECENT_RUNS_SQL = BASE_RUN_COLUMNS + """
        ORDER BY migration_run_id DESC
        LIMIT :limit
        """;

    private static final String FIND_RUN_BY_ID_SQL = BASE_RUN_COLUMNS + """
        WHERE migration_run_id = :migrationRunId
        """;

    private static final String FIND_DETAILS_BY_RUN_ID_SQL = BASE_DETAIL_COLUMNS + """
        WHERE migration_run_id = :migrationRunId
        ORDER BY migration_run_detail_id
        """;

    private static final String FIND_DETAILS_BY_RUN_ID_AND_RESULT_TYPE_SQL = BASE_DETAIL_COLUMNS + """
        WHERE migration_run_id = :migrationRunId
          AND result_type = :resultType
        ORDER BY migration_run_detail_id
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public MigrationRunRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Long createRun(String migrationType, String fileName, String sheetName, int totalRows, String uploadedBy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("migrationType", migrationType)
            .addValue("fileName", fileName)
            .addValue("sheetName", sheetName)
            .addValue("totalRows", totalRows)
            .addValue("status", MigrationAuditConstants.RunStatuses.RUNNING)
            .addValue("uploadedBy", uploadedBy);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(CREATE_RUN_SQL, params, keyHolder, new String[]{"migration_run_id"});

        Number migrationRunId = keyHolder.getKey();
        if (migrationRunId != null) {
            return migrationRunId.longValue();
        }

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null) {
            throw new IllegalStateException("Unable to retrieve generated migration_run_id");
        }

        Object generatedId = keys.get("migration_run_id");
        if (generatedId instanceof Number number) {
            return number.longValue();
        }

        throw new IllegalStateException("Unable to retrieve generated migration_run_id");
    }

    public void saveDetail(
        Long migrationRunId,
        Integer excelRow,
        String referenceKey,
        String resultType,
        String reason,
        String sourceData
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("migrationRunId", migrationRunId)
            .addValue("excelRow", excelRow)
            .addValue("referenceKey", referenceKey)
            .addValue("resultType", resultType)
            .addValue("reason", reason)
            .addValue("sourceData", sourceData);

        namedParameterJdbcTemplate.update(SAVE_DETAIL_SQL, params);
    }

    public void completeRun(
        Long migrationRunId,
        int insertedCount,
        int duplicateCount,
        int failedCount,
        String status
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("migrationRunId", migrationRunId)
            .addValue("insertedCount", insertedCount)
            .addValue("duplicateCount", duplicateCount)
            .addValue("failedCount", failedCount)
            .addValue("status", status);

        namedParameterJdbcTemplate.update(COMPLETE_RUN_SQL, params);
    }

    public void markRunFailed(
        Long migrationRunId,
        int insertedCount,
        int duplicateCount,
        int failedCount,
        String reason
    ) {
        completeRun(
            migrationRunId,
            insertedCount,
            duplicateCount,
            failedCount,
            MigrationAuditConstants.RunStatuses.FAILED
        );
    }

    public List<MigrationRun> findRecentRuns(int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", limit);

        return namedParameterJdbcTemplate.query(FIND_RECENT_RUNS_SQL, params, migrationRunRowMapper());
    }

    public Optional<MigrationRun> findRunById(Long migrationRunId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("migrationRunId", migrationRunId);

        List<MigrationRun> runs = namedParameterJdbcTemplate.query(FIND_RUN_BY_ID_SQL, params, migrationRunRowMapper());
        return runs.stream().findFirst();
    }

    public List<MigrationRunDetail> findDetailsByRunId(Long migrationRunId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("migrationRunId", migrationRunId);

        return namedParameterJdbcTemplate.query(FIND_DETAILS_BY_RUN_ID_SQL, params, migrationRunDetailRowMapper());
    }

    public List<MigrationRunDetail> findDetailsByRunId(Long migrationRunId, String resultType) {
        if (resultType == null || resultType.isBlank()) {
            return findDetailsByRunId(migrationRunId);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("migrationRunId", migrationRunId)
            .addValue("resultType", resultType);

        return namedParameterJdbcTemplate.query(
            FIND_DETAILS_BY_RUN_ID_AND_RESULT_TYPE_SQL,
            params,
            migrationRunDetailRowMapper()
        );
    }

    private RowMapper<MigrationRun> migrationRunRowMapper() {
        return (rs, rowNum) -> new MigrationRun(
            rs.getObject("migration_run_id", Long.class),
            rs.getString("migration_type"),
            rs.getString("file_name"),
            rs.getString("sheet_name"),
            rs.getInt("total_rows"),
            rs.getInt("inserted_count"),
            rs.getInt("duplicate_count"),
            rs.getInt("failed_count"),
            rs.getString("status"),
            rs.getString("uploaded_by"),
            toLocalDateTime(rs.getTimestamp("started_at")),
            toLocalDateTime(rs.getTimestamp("completed_at")),
            toLocalDateTime(rs.getTimestamp("created_at"))
        );
    }

    private RowMapper<MigrationRunDetail> migrationRunDetailRowMapper() {
        return (rs, rowNum) -> new MigrationRunDetail(
            rs.getObject("migration_run_detail_id", Long.class),
            rs.getObject("migration_run_id", Long.class),
            rs.getObject("excel_row", Integer.class),
            rs.getString("reference_key"),
            rs.getString("result_type"),
            rs.getString("reason"),
            rs.getString("source_data"),
            toLocalDateTime(rs.getTimestamp("created_at"))
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
