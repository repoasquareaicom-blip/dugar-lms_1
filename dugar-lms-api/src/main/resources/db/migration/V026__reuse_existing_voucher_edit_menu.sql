WITH existing_voucher_edit AS (
    UPDATE menus
    SET
        menu_name = 'Voucher Edit',
        url_path = '/accounts/transaction/edit',
        icon = 'FilePenLine',
        display_order = 2,
        menu_type = 'MENU',
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE menu_code = 'VOUCHER_EDIT_88'
    RETURNING menu_id
),
edit_children AS (
    UPDATE menus
    SET
        parent_id = existing_voucher_edit.menu_id,
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    FROM existing_voucher_edit
    WHERE menu_code IN (
        'EDIT_PAYMENT_VOUCHER_CASH',
        'EDIT_PAYMENT_VOUCHER_BANK',
        'EDIT_RECEIPT_VOUCHER_CASH',
        'EDIT_RECEIPT_VOUCHER_BANK',
        'EDIT_JOURNAL_VOUCHER'
    )
    RETURNING menus.menu_id
),
duplicate_edit_voucher AS (
    UPDATE menus
    SET
        is_active = FALSE,
        is_visible = FALSE,
        updated_at = CURRENT_TIMESTAMP
    WHERE menu_code = 'EDIT_VOUCHER'
    RETURNING menu_id
),
admin_roles AS (
    SELECT role_id
    FROM roles
    WHERE is_active = TRUE
      AND (
          UPPER(role_name) = 'SUPER_ADMIN'
          OR UPPER(role_name) = 'ADMIN'
          OR UPPER(role_code) LIKE 'SUPER_ADMIN%'
          OR UPPER(role_code) LIKE 'ADMIN%'
      )
),
permission_menus AS (
    SELECT menu_id FROM existing_voucher_edit
    UNION
    SELECT menu_id FROM edit_children
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
    admin_roles.role_id,
    permission_menus.menu_id,
    TRUE,
    TRUE,
    TRUE,
    TRUE
FROM admin_roles
CROSS JOIN permission_menus
ON CONFLICT (role_id, menu_id) DO UPDATE
SET
    can_view = TRUE,
    can_add = TRUE,
    can_edit = TRUE,
    can_delete = TRUE,
    updated_at = CURRENT_TIMESTAMP;
