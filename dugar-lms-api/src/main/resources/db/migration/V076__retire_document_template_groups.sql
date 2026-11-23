-- Forward-only: preserves template IDs, files, languages and historical group tables/audits.
-- Without CASCADE: unexpected external dependencies fail safely for operator review.
ALTER TABLE word_templates DROP COLUMN IF EXISTS group_id;
-- A global active variant must be unambiguous. Existing collisions deliberately fail
-- this transaction rather than silently deleting or deactivating a template.
DROP INDEX IF EXISTS uq_document_active_language;
DROP INDEX IF EXISTS idx_document_language;
CREATE UNIQUE INDEX uq_document_active_language
    ON word_templates (LOWER(BTRIM(document_key)), language_code) WHERE is_active;
CREATE INDEX IF NOT EXISTS idx_document_language ON word_templates(language_code, is_active);
