-- Definitions can exist before an administrator uploads the real, language-specific DOCX.
ALTER TABLE word_templates ALTER COLUMN original_filename DROP NOT NULL;
ALTER TABLE word_templates ALTER COLUMN storage_path DROP NOT NULL;
CREATE TABLE IF NOT EXISTS word_template_file_audit (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES word_templates(id) ON DELETE RESTRICT,
    action VARCHAR(16) NOT NULL CHECK (action IN ('UPLOAD','REPLACE','ACTIVATE','DEACTIVATE')),
    original_filename VARCHAR(255), storage_path TEXT, file_hash VARCHAR(64),
    changed_by VARCHAR(100), changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_word_template_file_audit_template
    ON word_template_file_audit(template_id, changed_at);
-- Do not hard-code local IDs 6/7, alter existing variants, or fabricate DOCX files.
-- Repair a lagging sequence before inserting only genuinely missing definitions.
SELECT setval(pg_get_serial_sequence('word_templates','id'),
    GREATEST((SELECT last_value FROM word_templates_id_seq), COALESCE((SELECT MAX(id) FROM word_templates),1)), true);
INSERT INTO word_templates(template_name,document_key,language_code,is_active,created_by,updated_by)
SELECT 'Reminder Letter','Reminder Letter',language,'false'::boolean,'flyway','flyway'
FROM (VALUES ('en'),('hi')) AS languages(language)
WHERE NOT EXISTS (
    SELECT 1 FROM word_templates t WHERE t.language_code=language
    AND (LOWER(REGEXP_REPLACE(BTRIM(t.document_key),'[ _]+',' ','g'))='reminder letter'
      OR LOWER(REGEXP_REPLACE(BTRIM(t.template_name),'[ _]+',' ','g'))='reminder letter')
);
