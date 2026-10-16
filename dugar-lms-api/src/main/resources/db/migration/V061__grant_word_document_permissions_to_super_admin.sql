WITH super_admin_roles AS (
    SELECT role_id
    FROM roles
    WHERE is_active = TRUE
      AND (
          UPPER(REGEXP_REPLACE(TRIM(role_name), '[^A-Za-z0-9]+', '_', 'g')) IN ('SUPER_ADMIN', 'SUPER_ADMIN_1')
          OR UPPER(REGEXP_REPLACE(TRIM(role_code), '[^A-Za-z0-9]+', '_', 'g')) IN ('SUPER_ADMIN', 'SUPER_ADMIN_1')
          OR UPPER(REGEXP_REPLACE(TRIM(role_code), '[^A-Za-z0-9]+', '_', 'g')) LIKE 'SUPER_ADMIN\_%' ESCAPE '\'
      )
),
word_document_menus AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code IN (
        'ADMINISTRATION',
        'ADMINISTRATION_MASTERS',
        'ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER',
        'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT'
    )
)
INSERT INTO role_permissions (
    role_id,
    menu_id,
    can_view,
    can_add,
    can_edit,
    can_delete
)
SELECT
    super_admin_roles.role_id,
    word_document_menus.menu_id,
    TRUE,
    FALSE,
    FALSE,
    FALSE
FROM super_admin_roles
CROSS JOIN word_document_menus
ON CONFLICT (role_id, menu_id) DO UPDATE
SET
    can_view = TRUE,
    updated_at = CURRENT_TIMESTAMP;
