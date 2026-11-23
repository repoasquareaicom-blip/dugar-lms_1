-- Groups remain as legacy storage/audit relationships; the document UI no longer exposes them.
-- Preserve all existing memberships, files, ordering and statuses.
INSERT INTO word_template_groups(group_name,description,data_source,display_order,is_active)
VALUES ('Document Templates','Internal default for templates created without a group selector','CONTRACT',0,TRUE)
ON CONFLICT(group_name) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM word_template_groups WHERE group_name='Document Templates' AND data_source='CONTRACT') THEN
        RAISE EXCEPTION 'Internal Document Templates group exists with an incompatible data source';
    END IF;
END $$;
