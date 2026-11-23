package dugar_lms_api.modules.wordtemplates.repository;

import dugar_lms_api.modules.wordtemplates.dto.*;
import dugar_lms_api.modules.wordtemplates.model.WordTemplateFile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class WordTemplateRepository {
    private static final String COLUMNS="id,template_name,language_code,document_key,original_filename,display_order,is_active,created_at,created_by,updated_at,updated_by";
    private static final RowMapper<WordTemplateDto> MAPPER=(rs,n) -> new WordTemplateDto(rs.getLong("id"),rs.getString("template_name"),rs.getString("original_filename"),rs.getInt("display_order"),rs.getBoolean("is_active"),rs.getObject("created_at",java.time.LocalDateTime.class),rs.getString("created_by"),rs.getObject("updated_at",java.time.LocalDateTime.class),rs.getString("updated_by"),null,rs.getString("language_code"),rs.getString("document_key"));
    private final NamedParameterJdbcTemplate jdbc;
    public WordTemplateRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<WordTemplateDto> findTemplates(String keyword,Boolean active) {
        return jdbc.query("SELECT "+COLUMNS+" FROM word_templates WHERE (:active::boolean IS NULL OR is_active=:active) AND (:keyword::text IS NULL OR LOWER(template_name) LIKE :keyword OR LOWER(original_filename) LIKE :keyword OR LOWER(document_key) LIKE :keyword) ORDER BY display_order,template_name,language_code,id",
            new MapSqlParameterSource("active",active).addValue("keyword",keyword==null||keyword.isBlank()?null:"%"+keyword.trim().toLowerCase(Locale.ROOT)+"%"),MAPPER);
    }
    public Optional<WordTemplateDto> findTemplate(Long id){return jdbc.query("SELECT "+COLUMNS+" FROM word_templates WHERE id=:id",new MapSqlParameterSource("id",id),MAPPER).stream().findFirst();}
    public void lock(Long id){jdbc.queryForList("SELECT id FROM word_templates WHERE id=:id FOR UPDATE",new MapSqlParameterSource("id",id));}
    public Optional<WordTemplateFile> findTemplateFile(Long id){return jdbc.query("SELECT id,original_filename,storage_path FROM word_templates WHERE id=:id",new MapSqlParameterSource("id",id),(rs,n)->new WordTemplateFile(rs.getLong(1),rs.getString(2),rs.getString(3))).stream().findFirst();}
    // Definition registration is reserved for Flyway/developer code, never exposed by the administration service.
    public WordTemplateDto createTemplate(WordTemplateMetadataRequest request,String filename,String path,String user) {
        return jdbc.queryForObject("INSERT INTO word_templates(template_name,document_key,language_code,original_filename,storage_path,display_order,is_active,created_by,updated_by) VALUES(:name,:key,:lang,:file,:path,:order,:active,:user,:user) RETURNING "+COLUMNS,
            new MapSqlParameterSource("name",request.templateName()).addValue("key",request.documentKey()).addValue("lang",request.languageCode()).addValue("file",filename).addValue("path",path).addValue("order",request.displayOrder()==null?0:request.displayOrder()).addValue("active",Boolean.TRUE.equals(request.isActive())).addValue("user",user),MAPPER);
    }
    public WordTemplateDto replaceTemplateFile(Long id,String filename,String path,String user){return jdbc.queryForObject("UPDATE word_templates SET original_filename=:file,storage_path=:path,updated_at=CURRENT_TIMESTAMP,updated_by=:user WHERE id=:id RETURNING "+COLUMNS,new MapSqlParameterSource("id",id).addValue("file",filename).addValue("path",path).addValue("user",user),MAPPER);}
    public WordTemplateDto setActive(Long id,boolean active,String user){return jdbc.queryForObject("UPDATE word_templates SET is_active=:active,updated_at=CURRENT_TIMESTAMP,updated_by=:user WHERE id=:id RETURNING "+COLUMNS,new MapSqlParameterSource("id",id).addValue("active",active).addValue("user",user),MAPPER);}
    public void audit(Long id,String action,String filename,String path,String hash,String user){jdbc.update("INSERT INTO word_template_file_audit(template_id,action,original_filename,storage_path,file_hash,changed_by) VALUES(:id,:action,:file,:path,:hash,:user)",new MapSqlParameterSource("id",id).addValue("action",action).addValue("file",filename).addValue("path",path).addValue("hash",hash).addValue("user",user));}
}
