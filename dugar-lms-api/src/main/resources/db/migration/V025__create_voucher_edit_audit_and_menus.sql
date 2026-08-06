CREATE TABLE IF NOT EXISTS voucher_audit_trails (
    voucher_audit_id BIGSERIAL PRIMARY KEY,
    voucher_header_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    before_snapshot TEXT,
    after_snapshot TEXT,
    changed_by BIGINT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks TEXT,

    CONSTRAINT fk_voucher_audit_header_id
        FOREIGN KEY (voucher_header_id)
        REFERENCES voucher_headers (voucher_header_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_voucher_audit_header_id
    ON voucher_audit_trails (voucher_header_id);

WITH transaction_menu AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'TRANSACTION_82'
),
edit_voucher AS (
    INSERT INTO menus (
        menu_name,
        parent_id,
        url_path,
        icon,
        display_order,
        menu_code,
        menu_type,
        is_active,
        is_visible
    )
    SELECT
        'Edit Voucher',
        menu_id,
        '/accounts/transaction/edit-voucher',
        'FilePenLine',
        3,
        'EDIT_VOUCHER',
        'MENU',
        TRUE,
        TRUE
    FROM transaction_menu
    ON CONFLICT (menu_code) DO UPDATE
    SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        url_path = EXCLUDED.url_path,
        icon = EXCLUDED.icon,
        display_order = EXCLUDED.display_order,
        menu_type = EXCLUDED.menu_type,
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING menu_id
),
edit_children AS (
    INSERT INTO menus (
        menu_name,
        parent_id,
        url_path,
        icon,
        display_order,
        menu_code,
        menu_type,
        is_active,
        is_visible
    )
    SELECT child.menu_name, edit_voucher.menu_id, child.url_path, child.icon, child.display_order, child.menu_code, 'PAGE', TRUE, TRUE
    FROM edit_voucher
    CROSS JOIN (
        VALUES
            ('Edit Payment Voucher - Cash', '/accounts/transaction/edit/payment/cash', 'Banknote', 1, 'EDIT_PAYMENT_VOUCHER_CASH'),
            ('Edit Payment Voucher - Bank', '/accounts/transaction/edit/payment/bank', 'Landmark', 2, 'EDIT_PAYMENT_VOUCHER_BANK'),
            ('Edit Receipt Voucher - Cash', '/accounts/transaction/edit/receipt/cash', 'ReceiptIndianRupee', 3, 'EDIT_RECEIPT_VOUCHER_CASH'),
            ('Edit Receipt Voucher - Bank', '/accounts/transaction/edit/receipt/bank', 'Landmark', 4, 'EDIT_RECEIPT_VOUCHER_BANK'),
            ('Edit Journal Voucher', '/accounts/transaction/edit/journal', 'BookOpen', 5, 'EDIT_JOURNAL_VOUCHER')
    ) AS child(menu_name, url_path, icon, display_order, menu_code)
    ON CONFLICT (menu_code) DO UPDATE
    SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        url_path = EXCLUDED.url_path,
        icon = EXCLUDED.icon,
        display_order = EXCLUDED.display_order,
        menu_type = EXCLUDED.menu_type,
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
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
    SELECT menu_id FROM edit_voucher
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
