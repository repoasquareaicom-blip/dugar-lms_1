-- Read-only preflight/post-migration inspection. No manual data updates.
BEGIN TRANSACTION READ ONLY;

SELECT current_database(), inet_server_addr(), inet_server_port();
SELECT version, description, script, success
FROM flyway_schema_history WHERE version IN ('060', '061', '062', '063', '067')
ORDER BY installed_rank;

SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('menus', 'roles', 'role_permissions')
ORDER BY table_name, ordinal_position;

SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid IN ('menus'::regclass, 'roles'::regclass, 'role_permissions'::regclass);

-- Expected paths: Administration -> Masters -> Document Template Master;
-- Operations -> Transaction(s) -> Generate Document. Legacy ancestor codes remain unchanged.
WITH RECURSIVE document_tree AS (
    SELECT m.menu_id, m.parent_id, m.menu_code, m.menu_name, m.url_path,
           m.is_active, m.is_visible, ARRAY[m.menu_name]::text[] AS path, ARRAY[m.menu_id] AS visited
    FROM menus m WHERE m.parent_id IS NULL
    UNION ALL
    SELECT m.menu_id, m.parent_id, m.menu_code, m.menu_name, m.url_path,
           m.is_active, m.is_visible, tree.path || m.menu_name::text, tree.visited || m.menu_id
    FROM menus m JOIN document_tree tree ON m.parent_id = tree.menu_id
    WHERE NOT m.menu_id = ANY(tree.visited)
)
SELECT menu_id, parent_id, menu_code, menu_name, url_path, is_active, is_visible,
       array_to_string(path, ' -> ') AS hierarchy
FROM document_tree
WHERE menu_code IN ('ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER',
                    'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT');

SELECT r.role_id, r.role_code, r.role_name, r.is_active,
       m.menu_code, m.menu_name, p.can_view, p.can_add, p.can_edit, p.can_delete
FROM roles r CROSS JOIN menus m
LEFT JOIN role_permissions p ON p.role_id = r.role_id AND p.menu_id = m.menu_id
WHERE m.menu_code IN ('ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER',
                      'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT')
ORDER BY r.role_code, m.menu_code;

-- Post-migration assertion: expect ZERO rows. Includes view access and
-- visibility for every required ancestor, without requiring any CRUD grants.
WITH RECURSIVE required_menus AS (
    SELECT m.menu_id, m.parent_id, m.menu_code,
           role_kind.kind AS role_kind, ARRAY[m.menu_id] AS visited
    FROM menus m CROSS JOIN (VALUES ('SUPER_ADMIN'), ('OPERATIONS')) role_kind(kind)
    WHERE m.menu_code = 'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT'
       OR (m.menu_code = 'ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER' AND role_kind.kind = 'SUPER_ADMIN')
    UNION ALL
    SELECT m.menu_id, m.parent_id, m.menu_code, child.role_kind, child.visited || m.menu_id
    FROM required_menus child JOIN menus m ON m.menu_id = child.parent_id
    WHERE NOT m.menu_id = ANY(child.visited)
), expected_roles AS (
    SELECT role_id, role_code, is_active,
        CASE WHEN UPPER(TRIM(role_code)) = 'SUPER_ADMIN'
                   OR UPPER(REGEXP_REPLACE(TRIM(role_name), '[^A-Za-z0-9]+', '_', 'g')) = 'SUPER_ADMIN'
             THEN 'SUPER_ADMIN' ELSE 'OPERATIONS' END AS role_kind
    FROM roles
    WHERE UPPER(TRIM(role_code)) IN ('SUPER_ADMIN', 'OPERATIONS')
       OR UPPER(REGEXP_REPLACE(TRIM(role_name), '[^A-Za-z0-9]+', '_', 'g')) IN ('SUPER_ADMIN', 'OPERATIONS')
)
SELECT DISTINCT r.role_code, m.menu_code, 'Missing required view access or inactive/hidden record' AS concern
FROM expected_roles r JOIN required_menus required ON required.role_kind = r.role_kind
JOIN menus m ON m.menu_id = required.menu_id
LEFT JOIN role_permissions p ON p.role_id = r.role_id AND p.menu_id = m.menu_id
WHERE r.is_active AND (NOT m.is_active OR NOT m.is_visible OR NOT COALESCE(p.can_view, FALSE))
UNION ALL
SELECT expected.kind, NULL, 'Required active role is missing'
FROM (VALUES ('SUPER_ADMIN'), ('OPERATIONS')) expected(kind)
WHERE NOT EXISTS (SELECT 1 FROM expected_roles r WHERE r.role_kind = expected.kind AND r.is_active)
UNION ALL
SELECT NULL, expected.code, 'Required document menu is missing'
FROM (VALUES ('ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER'),
             ('OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT')) expected(code)
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.menu_code = expected.code);

-- Expect exactly two rows with the new labels and the ORIGINAL codes/routes.
SELECT menu_code, menu_name, url_path FROM menus
WHERE menu_code IN ('ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER',
                    'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT');
COMMIT;
