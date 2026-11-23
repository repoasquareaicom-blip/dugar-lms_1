-- Keep the V060 codes, routes, DOCX tables and existing CRUD permissions.
-- Resolve legacy ancestors by name only when a canonical code is unavailable.
DO $$
DECLARE
    administration_id BIGINT;
    masters_id BIGINT;
    operations_id BIGINT;
    transactions_id BIGINT;
    template_id BIGINT;
    generate_id BIGINT;
BEGIN
    SELECT menu_id INTO STRICT template_id FROM menus
    WHERE menu_code = 'ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER';
    SELECT menu_id INTO STRICT generate_id FROM menus
    WHERE menu_code = 'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT';

    SELECT menu_id INTO administration_id FROM menus
    WHERE menu_code = 'ADMINISTRATION' AND parent_id IS NULL;
    IF administration_id IS NULL THEN
        SELECT menu_id INTO STRICT administration_id FROM menus
        WHERE parent_id IS NULL AND UPPER(TRIM(menu_name)) = 'ADMINISTRATION';
    END IF;

    SELECT menu_id INTO masters_id FROM menus
    WHERE menu_code = 'ADMINISTRATION_MASTERS' AND parent_id = administration_id;
    IF masters_id IS NULL THEN
        SELECT menu_id INTO STRICT masters_id FROM menus
        WHERE parent_id = administration_id AND UPPER(TRIM(menu_name)) = 'MASTERS';
    END IF;

    SELECT menu_id INTO operations_id FROM menus
    WHERE menu_code = 'OPERATIONS' AND parent_id IS NULL;
    IF operations_id IS NULL THEN
        SELECT menu_id INTO STRICT operations_id FROM menus
        WHERE parent_id IS NULL AND UPPER(TRIM(menu_name)) = 'OPERATIONS';
    END IF;

    SELECT menu_id INTO transactions_id FROM menus
    WHERE parent_id = operations_id AND menu_code = 'OPERATIONS_TRANSACTIONS';
    IF transactions_id IS NULL THEN
        SELECT menu_id INTO transactions_id FROM menus
        WHERE parent_id = operations_id AND menu_code = 'OPERATIONS_TRANSACTION';
    END IF;
    IF transactions_id IS NULL THEN
        SELECT menu_id INTO STRICT transactions_id FROM menus
        WHERE parent_id = operations_id AND UPPER(TRIM(menu_name)) IN ('TRANSACTION', 'TRANSACTIONS');
    END IF;

    -- Missing/ambiguous ancestors fail transactionally instead of silently
    -- creating a duplicate tree or choosing an arbitrary environment-specific ID.
    IF NOT EXISTS (SELECT 1 FROM roles WHERE is_active AND
        (UPPER(TRIM(role_code)) = 'SUPER_ADMIN'
         OR UPPER(REGEXP_REPLACE(TRIM(role_name), '[^A-Za-z0-9]+', '_', 'g')) = 'SUPER_ADMIN'))
       OR NOT EXISTS (SELECT 1 FROM roles WHERE is_active AND
        (UPPER(TRIM(role_code)) = 'OPERATIONS'
         OR UPPER(REGEXP_REPLACE(TRIM(role_name), '[^A-Za-z0-9]+', '_', 'g')) = 'OPERATIONS')) THEN
        RAISE EXCEPTION 'Document menus require existing active SUPER_ADMIN and OPERATIONS roles';
    END IF;

    UPDATE menus SET menu_name = 'Document Template Master', parent_id = masters_id,
        is_active = TRUE, is_visible = TRUE, updated_at = CURRENT_TIMESTAMP
    WHERE menu_id = template_id;
    UPDATE menus SET menu_name = 'Generate Document', parent_id = transactions_id,
        is_active = TRUE, is_visible = TRUE, updated_at = CURRENT_TIMESTAMP
    WHERE menu_id = generate_id;
    UPDATE menus SET is_active = TRUE, is_visible = TRUE, updated_at = CURRENT_TIMESTAMP
    WHERE menu_id IN (administration_id, masters_id, operations_id, transactions_id)
      AND (NOT is_active OR NOT is_visible);

    INSERT INTO role_permissions (role_id, menu_id, can_view, can_add, can_edit, can_delete)
    SELECT r.role_id, target.menu_id, TRUE, FALSE, FALSE, FALSE
    FROM roles r CROSS JOIN (VALUES
        (administration_id), (masters_id), (template_id),
        (operations_id), (transactions_id), (generate_id)
    ) AS target(menu_id)
    WHERE r.is_active AND
        (UPPER(TRIM(r.role_code)) = 'SUPER_ADMIN'
         OR UPPER(REGEXP_REPLACE(TRIM(r.role_name), '[^A-Za-z0-9]+', '_', 'g')) = 'SUPER_ADMIN')
    ON CONFLICT (role_id, menu_id) DO UPDATE
    SET can_view = TRUE, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO role_permissions (role_id, menu_id, can_view, can_add, can_edit, can_delete)
    SELECT r.role_id, target.menu_id, TRUE, FALSE, FALSE, FALSE
    FROM roles r CROSS JOIN (VALUES
        (operations_id), (transactions_id), (generate_id)
    ) AS target(menu_id)
    WHERE r.is_active AND
        (UPPER(TRIM(r.role_code)) = 'OPERATIONS'
         OR UPPER(REGEXP_REPLACE(TRIM(r.role_name), '[^A-Za-z0-9]+', '_', 'g')) = 'OPERATIONS')
    ON CONFLICT (role_id, menu_id) DO UPDATE
    SET can_view = TRUE, updated_at = CURRENT_TIMESTAMP;
END $$;
