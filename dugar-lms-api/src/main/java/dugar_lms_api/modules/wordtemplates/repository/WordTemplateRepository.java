package dugar_lms_api.modules.wordtemplates.repository;

import dugar_lms_api.modules.wordtemplates.dto.WordTemplateDto;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateGroupDto;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateGroupRequest;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateMetadataRequest;
import dugar_lms_api.modules.wordtemplates.model.WordTemplateFile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class WordTemplateRepository {

    private static final RowMapper<WordTemplateGroupDto> GROUP_MAPPER = (rs, rowNum) -> new WordTemplateGroupDto(
        rs.getLong("id"),
        rs.getString("group_name"),
        rs.getString("description"),
        rs.getString("data_source"),
        rs.getObject("display_order", Integer.class),
        rs.getObject("is_active", Boolean.class),
        rs.getObject("created_at", java.time.LocalDateTime.class),
        rs.getString("created_by"),
        rs.getObject("updated_at", java.time.LocalDateTime.class),
        rs.getString("updated_by"),
        rs.getObject("template_count", Integer.class)
    );

    private static final RowMapper<WordTemplateDto> TEMPLATE_MAPPER = (rs, rowNum) -> new WordTemplateDto(
        rs.getLong("id"),
        rs.getLong("group_id"),
        rs.getString("group_name"),
        rs.getString("template_name"),
        rs.getString("original_filename"),
        rs.getObject("display_order", Integer.class),
        rs.getObject("is_active", Boolean.class),
        rs.getObject("created_at", java.time.LocalDateTime.class),
        rs.getString("created_by"),
        rs.getObject("updated_at", java.time.LocalDateTime.class),
        rs.getString("updated_by"),
        null
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WordTemplateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WordTemplateGroupDto> findGroups(String keyword, Boolean isActive) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("keyword", like(keyword))
            .addValue("isActive", isActive);
        return jdbcTemplate.query(
            """
            SELECT
                g.id,
                g.group_name,
                g.description,
                g.data_source,
                g.display_order,
                g.is_active,
                g.created_at,
                g.created_by,
                g.updated_at,
                g.updated_by,
                COUNT(t.id)::int AS template_count
            FROM word_template_groups g
            LEFT JOIN word_templates t
              ON t.group_id = g.id
            WHERE (CAST(:isActive AS boolean) IS NULL OR g.is_active = CAST(:isActive AS boolean))
              AND (CAST(:keyword AS text) IS NULL OR LOWER(g.group_name) LIKE CAST(:keyword AS text) OR LOWER(COALESCE(g.description, '')) LIKE CAST(:keyword AS text))
            GROUP BY g.id
            ORDER BY g.display_order, g.group_name
            """,
            params,
            GROUP_MAPPER
        );
    }

    public Optional<WordTemplateGroupDto> findGroup(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                SELECT
                    g.id,
                    g.group_name,
                    g.description,
                    g.data_source,
                    g.display_order,
                    g.is_active,
                    g.created_at,
                    g.created_by,
                    g.updated_at,
                    g.updated_by,
                    COUNT(t.id)::int AS template_count
                FROM word_template_groups g
                LEFT JOIN word_templates t
                  ON t.group_id = g.id
                WHERE g.id = :id
                GROUP BY g.id
                """,
                new MapSqlParameterSource("id", id),
                GROUP_MAPPER
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public WordTemplateGroupDto createGroup(WordTemplateGroupRequest request, String user) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO word_template_groups (
                group_name,
                description,
                data_source,
                display_order,
                is_active,
                created_by,
                updated_by
            )
            VALUES (
                :groupName,
                :description,
                :dataSource,
                :displayOrder,
                :isActive,
                :user,
                :user
            )
            RETURNING id, group_name, description, data_source, display_order, is_active, created_at, created_by, updated_at, updated_by, 0::int AS template_count
            """,
            groupParams(request).addValue("user", user),
            GROUP_MAPPER
        );
    }

    public boolean groupNameExists(String groupName, Long excludeId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM word_template_groups
                WHERE LOWER(TRIM(group_name)) = LOWER(TRIM(:groupName))
                  AND (CAST(:excludeId AS bigint) IS NULL OR id <> CAST(:excludeId AS bigint))
            )
            """,
            new MapSqlParameterSource()
                .addValue("groupName", groupName)
                .addValue("excludeId", excludeId),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public WordTemplateGroupDto updateGroup(Long id, WordTemplateGroupRequest request, String user) {
        return jdbcTemplate.queryForObject(
            """
            UPDATE word_template_groups
            SET
                group_name = :groupName,
                description = :description,
                data_source = :dataSource,
                display_order = :displayOrder,
                is_active = :isActive,
                updated_by = :user
            WHERE id = :id
            RETURNING id, group_name, description, data_source, display_order, is_active, created_at, created_by, updated_at, updated_by,
                (SELECT COUNT(*)::int FROM word_templates WHERE group_id = word_template_groups.id) AS template_count
            """,
            groupParams(request)
                .addValue("id", id)
                .addValue("user", user),
            GROUP_MAPPER
        );
    }

    public int countTemplates(Long groupId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*)::int FROM word_templates WHERE group_id = :groupId",
            new MapSqlParameterSource("groupId", groupId),
            Integer.class
        );
        return count == null ? 0 : count;
    }

    public void deleteGroup(Long id) {
        jdbcTemplate.update("DELETE FROM word_template_groups WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    public List<WordTemplateDto> findTemplates(Long groupId, String keyword, Boolean isActive) {
        return jdbcTemplate.query(
            """
            SELECT
                t.id,
                t.group_id,
                g.group_name,
                t.template_name,
                t.original_filename,
                t.display_order,
                t.is_active,
                t.created_at,
                t.created_by,
                t.updated_at,
                t.updated_by
            FROM word_templates t
            JOIN word_template_groups g
              ON g.id = t.group_id
            WHERE (CAST(:groupId AS bigint) IS NULL OR t.group_id = CAST(:groupId AS bigint))
              AND (CAST(:isActive AS boolean) IS NULL OR t.is_active = CAST(:isActive AS boolean))
              AND (CAST(:keyword AS text) IS NULL OR LOWER(t.template_name) LIKE CAST(:keyword AS text) OR LOWER(t.original_filename) LIKE CAST(:keyword AS text))
            ORDER BY g.display_order, g.group_name, t.display_order, t.template_name
            """,
            new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("keyword", like(keyword))
                .addValue("isActive", isActive),
            TEMPLATE_MAPPER
        );
    }

    public Optional<WordTemplateDto> findTemplate(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                SELECT
                    t.id,
                    t.group_id,
                    g.group_name,
                    t.template_name,
                    t.original_filename,
                    t.display_order,
                    t.is_active,
                    t.created_at,
                    t.created_by,
                    t.updated_at,
                    t.updated_by
                FROM word_templates t
                JOIN word_template_groups g
                  ON g.id = t.group_id
                WHERE t.id = :id
                """,
                new MapSqlParameterSource("id", id),
                TEMPLATE_MAPPER
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<WordTemplateFile> findTemplateFile(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                SELECT id, original_filename, storage_path
                FROM word_templates
                WHERE id = :id
                """,
                new MapSqlParameterSource("id", id),
                (rs, rowNum) -> new WordTemplateFile(
                    rs.getLong("id"),
                    rs.getString("original_filename"),
                    rs.getString("storage_path")
                )
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public WordTemplateDto createTemplate(WordTemplateMetadataRequest request, String originalFilename, String storagePath, String user) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO word_templates (
                group_id,
                template_name,
                original_filename,
                storage_path,
                display_order,
                is_active,
                created_by,
                updated_by
            )
            VALUES (
                :groupId,
                :templateName,
                :originalFilename,
                :storagePath,
                :displayOrder,
                :isActive,
                :user,
                :user
            )
            RETURNING
                id,
                group_id,
                (SELECT group_name FROM word_template_groups WHERE id = word_templates.group_id) AS group_name,
                template_name,
                original_filename,
                display_order,
                is_active,
                created_at,
                created_by,
                updated_at,
                updated_by
            """,
            templateParams(request)
                .addValue("originalFilename", originalFilename)
                .addValue("storagePath", storagePath)
                .addValue("user", user),
            TEMPLATE_MAPPER
        );
    }

    public WordTemplateDto updateTemplate(Long id, WordTemplateMetadataRequest request, String user) {
        return jdbcTemplate.queryForObject(
            """
            UPDATE word_templates
            SET
                group_id = :groupId,
                template_name = :templateName,
                display_order = :displayOrder,
                is_active = :isActive,
                updated_by = :user
            WHERE id = :id
            RETURNING
                id,
                group_id,
                (SELECT group_name FROM word_template_groups WHERE id = word_templates.group_id) AS group_name,
                template_name,
                original_filename,
                display_order,
                is_active,
                created_at,
                created_by,
                updated_at,
                updated_by
            """,
            templateParams(request)
                .addValue("id", id)
                .addValue("user", user),
            TEMPLATE_MAPPER
        );
    }

    public WordTemplateDto replaceTemplateFile(Long id, String originalFilename, String storagePath, String user) {
        return jdbcTemplate.queryForObject(
            """
            UPDATE word_templates
            SET
                original_filename = :originalFilename,
                storage_path = :storagePath,
                updated_by = :user
            WHERE id = :id
            RETURNING
                id,
                group_id,
                (SELECT group_name FROM word_template_groups WHERE id = word_templates.group_id) AS group_name,
                template_name,
                original_filename,
                display_order,
                is_active,
                created_at,
                created_by,
                updated_at,
                updated_by
            """,
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("originalFilename", originalFilename)
                .addValue("storagePath", storagePath)
                .addValue("user", user),
            TEMPLATE_MAPPER
        );
    }

    public void deleteTemplate(Long id) {
        jdbcTemplate.update("DELETE FROM word_templates WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private MapSqlParameterSource groupParams(WordTemplateGroupRequest request) {
        return new MapSqlParameterSource()
            .addValue("groupName", clean(request.groupName()))
            .addValue("description", clean(request.description()))
            .addValue("dataSource", normalizeDataSource(request.dataSource()))
            .addValue("displayOrder", request.displayOrder() == null ? 0 : request.displayOrder())
            .addValue("isActive", request.isActive() == null || request.isActive());
    }

    private MapSqlParameterSource templateParams(WordTemplateMetadataRequest request) {
        return new MapSqlParameterSource()
            .addValue("groupId", request.groupId())
            .addValue("templateName", clean(request.templateName()))
            .addValue("displayOrder", request.displayOrder() == null ? 0 : request.displayOrder())
            .addValue("isActive", request.isActive() == null || request.isActive());
    }

    private String like(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : "%" + cleaned.toLowerCase() + "%";
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeDataSource(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "CONTRACT" : cleaned.toUpperCase();
    }
}
