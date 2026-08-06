WITH voucher_entry AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'VOUCHER_ENTRY_87'
),
updated_payment_cash AS (
    UPDATE menus
    SET
        menu_name = 'Payment Voucher - Cash',
        menu_code = 'PAYMENT_VOUCHER_CASH',
        url_path = '/accounts/transaction/entry/payment/cash',
        icon = 'Banknote',
        display_order = 1,
        menu_type = 'PAGE',
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE menu_code IN ('PAYMENT_VOUCHER_CASH_BANK_92', 'PAYMENT_VOUCHER_CASH')
    RETURNING menu_id
),
updated_payment_bank AS (
    UPDATE menus
    SET
        menu_name = 'Payment Voucher - Bank',
        menu_code = 'PAYMENT_VOUCHER_BANK',
        url_path = '/accounts/transaction/entry/payment/bank',
        icon = 'Landmark',
        display_order = 2,
        menu_type = 'PAGE',
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE menu_code IN ('RECEIPT_VOUCHER_CASH_BANK_93', 'PAYMENT_VOUCHER_BANK')
    RETURNING menu_id
),
insert_receipt_cash AS (
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
        'Receipt Voucher - Cash',
        menu_id,
        '/accounts/transaction/entry/receipt/cash',
        'ReceiptIndianRupee',
        3,
        'RECEIPT_VOUCHER_CASH',
        'PAGE',
        TRUE,
        TRUE
    FROM voucher_entry
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
insert_receipt_bank AS (
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
        'Receipt Voucher - Bank',
        menu_id,
        '/accounts/transaction/entry/receipt/bank',
        'Landmark',
        4,
        'RECEIPT_VOUCHER_BANK',
        'PAGE',
        TRUE,
        TRUE
    FROM voucher_entry
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
updated_journal AS (
    UPDATE menus
    SET
        menu_name = 'Journal Voucher',
        menu_code = 'JOURNAL_VOUCHER_94',
        url_path = '/accounts/transaction/entry/journal',
        icon = 'BookOpen',
        display_order = 5,
        menu_type = 'PAGE',
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE menu_code = 'JOURNAL_VOUCHER_94'
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
    SELECT menu_id FROM menus
    WHERE menu_code IN (
        'ACCOUNTS_80',
        'TRANSACTION_82',
        'VOUCHER_ENTRY_87',
        'JOURNAL_VOUCHER_94'
    )
    UNION
    SELECT menu_id FROM updated_payment_cash
    UNION
    SELECT menu_id FROM updated_payment_bank
    UNION
    SELECT menu_id FROM insert_receipt_cash
    UNION
    SELECT menu_id FROM insert_receipt_bank
    UNION
    SELECT menu_id FROM updated_journal
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
