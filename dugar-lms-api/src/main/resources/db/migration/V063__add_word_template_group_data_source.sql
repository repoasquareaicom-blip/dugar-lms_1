ALTER TABLE word_template_groups
    ADD COLUMN IF NOT EXISTS data_source VARCHAR(50) NOT NULL DEFAULT 'CONTRACT';

UPDATE word_template_groups
SET data_source = 'CONTRACT'
WHERE data_source IS NULL OR TRIM(data_source) = '';

CREATE INDEX IF NOT EXISTS idx_word_template_groups_data_source_active
    ON word_template_groups(data_source, is_active, display_order, group_name);
