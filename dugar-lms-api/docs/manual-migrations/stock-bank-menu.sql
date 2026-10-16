-- MANUAL ONLY. Register the transaction and authorized SUPER_ADMIN/CREDIT view permissions.
DO $$
DECLARE
    credit_id BIGINT;
    transaction_id BIGINT;
    stock_id BIGINT;
BEGIN
    SELECT menu_id INTO credit_id FROM menus
    WHERE parent_id IS NULL AND UPPER(TRIM(menu_name)) = 'CREDIT'
    ORDER BY CASE WHEN menu_code = 'CREDIT' THEN 0 ELSE 1 END, menu_id LIMIT 1;
    IF credit_id IS NULL THEN
        RAISE EXCEPTION 'Cannot integrate Stock Marking to Bank: CREDIT root menu is missing';
    END IF;

    SELECT menu_id INTO transaction_id FROM menus
    WHERE parent_id = credit_id
      AND (menu_code = 'CREDIT_TRANSACTION' OR UPPER(TRIM(menu_name)) IN ('TRANSACTION', 'TRANSACTIONS'))
    ORDER BY CASE WHEN menu_code = 'CREDIT_TRANSACTION' THEN 0 ELSE 1 END, menu_id LIMIT 1;
    IF transaction_id IS NULL THEN
        INSERT INTO menus (menu_name, parent_id, url_path, icon, display_order, menu_code, menu_type, is_active, is_visible)
        VALUES ('Transaction', credit_id, '#', 'FileText', 3, 'CREDIT_TRANSACTION', 'MENU', TRUE, TRUE)
        RETURNING menu_id INTO transaction_id;
    END IF;

    SELECT menu_id INTO stock_id FROM menus
    WHERE menu_code = 'CREDIT_TRANSACTION_STOCK_BANK_MARKING'
       OR url_path = '/credit/transaction/stock-bank-marking'
       OR (parent_id = transaction_id AND UPPER(TRIM(menu_name)) = 'STOCK MARKING TO BANK')
    ORDER BY CASE WHEN menu_code = 'CREDIT_TRANSACTION_STOCK_BANK_MARKING' THEN 0 ELSE 1 END, menu_id LIMIT 1;

    IF stock_id IS NULL THEN
        INSERT INTO menus (menu_name, parent_id, url_path, icon, display_order, menu_code, menu_type, is_active, is_visible)
        VALUES ('Stock Marking to Bank', transaction_id, '/credit/transaction/stock-bank-marking', 'FileSpreadsheet', 20,
            'CREDIT_TRANSACTION_STOCK_BANK_MARKING', 'PAGE', TRUE, TRUE)
        RETURNING menu_id INTO stock_id;
    ELSE
        UPDATE menus SET menu_name = 'Stock Marking to Bank', parent_id = transaction_id,
            url_path = '/credit/transaction/stock-bank-marking', menu_code = 'CREDIT_TRANSACTION_STOCK_BANK_MARKING',
            menu_type = 'PAGE', is_active = TRUE, is_visible = TRUE
        WHERE menu_id = stock_id;
    END IF;

    INSERT INTO role_permissions (role_id, menu_id, can_view, can_add, can_edit, can_delete)
    SELECT r.role_id, target.menu_id, TRUE, FALSE, FALSE, FALSE
    FROM roles r CROSS JOIN (VALUES (credit_id), (transaction_id), (stock_id)) target(menu_id)
    WHERE r.is_active = TRUE AND UPPER(TRIM(r.role_code)) IN ('SUPER_ADMIN', 'CREDIT')
    ON CONFLICT (role_id, menu_id) DO UPDATE SET can_view = TRUE;
    -- Preserve existing add/edit/delete flags on conflict.
END $$;
