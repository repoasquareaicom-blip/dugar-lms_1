CREATE TABLE document_languages (
    code VARCHAR(16) PRIMARY KEY, language_name VARCHAR(100) NOT NULL, is_active BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO document_languages(code, language_name) VALUES ('en','English'),('hi','Hindi');
ALTER TABLE word_templates ADD COLUMN language_code VARCHAR(16) NOT NULL DEFAULT 'en'
    REFERENCES document_languages(code);
ALTER TABLE word_templates ADD COLUMN document_key VARCHAR(150);
UPDATE word_templates SET document_key = template_name;
ALTER TABLE word_templates ALTER COLUMN document_key SET NOT NULL;
ALTER TABLE word_templates ADD CONSTRAINT chk_document_key CHECK (BTRIM(document_key) <> '');
ALTER TABLE word_templates DROP CONSTRAINT uq_word_templates_group_template_name;
CREATE UNIQUE INDEX uq_document_active_language ON word_templates(group_id, LOWER(BTRIM(document_key)), language_code)
    WHERE is_active;
CREATE INDEX idx_document_language ON word_templates(language_code, group_id, is_active);
