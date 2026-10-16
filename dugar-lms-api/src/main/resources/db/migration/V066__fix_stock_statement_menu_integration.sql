-- V065 was a no-op on legacy menu trees whose codes have numeric suffixes.
-- Preserve the applied migration and reuse the actual CREDIT / Reports records.
DO $$
DECLARE
    credit_id BIGINT;
    reports_id BIGINT;
    stock_id BIGINT;
BEGIN
    SELECT menu_id INTO credit_id FROM menus
    WHERE parent_id IS NULL AND UPPER(TRIM(menu_name)) = 'CREDIT'
    ORDER BY CASE WHEN menu_code = 'CREDIT' THEN 0 ELSE 1 END, menu_id LIMIT 1;
    IF credit_id IS NULL THEN
        RAISE EXCEPTION 'Cannot integrate Stock Statement: CREDIT root menu is missing';
    END IF;

    SELECT menu_id INTO reports_id FROM menus
    WHERE parent_id = credit_id
      AND (menu_code = 'CREDIT_REPORTS' OR UPPER(TRIM(menu_name)) = 'REPORTS')
    ORDER BY CASE WHEN menu_code = 'CREDIT_REPORTS' THEN 0 ELSE 1 END, menu_id LIMIT 1;
    IF reports_id IS NULL THEN
        INSERT INTO menus (menu_name, parent_id, url_path, icon, display_order, menu_code, menu_type, is_active, is_visible)
        VALUES ('Reports', credit_id, '#', 'FileText', 3, 'CREDIT_REPORTS', 'MENU', TRUE, TRUE)
        RETURNING menu_id INTO reports_id;
    END IF;

    SELECT menu_id INTO stock_id FROM menus
    WHERE menu_code = 'CREDIT_REPORTS_STOCK_STATEMENT'
       OR url_path = '/credit/reports/stock-statement'
       OR (parent_id = reports_id AND UPPER(TRIM(menu_name)) = 'STOCK STATEMENT')
    ORDER BY CASE WHEN menu_code = 'CREDIT_REPORTS_STOCK_STATEMENT' THEN 0 ELSE 1 END, menu_id LIMIT 1;

    IF stock_id IS NULL THEN
        INSERT INTO menus (menu_name, parent_id, url_path, icon, display_order, menu_code, menu_type, is_active, is_visible)
        VALUES ('Stock Statement', reports_id, '/credit/reports/stock-statement', 'FileSpreadsheet', 20,
            'CREDIT_REPORTS_STOCK_STATEMENT', 'PAGE', TRUE, TRUE)
        RETURNING menu_id INTO stock_id;
    ELSE
        UPDATE menus SET menu_name = 'Stock Statement', parent_id = reports_id,
            url_path = '/credit/reports/stock-statement', menu_code = 'CREDIT_REPORTS_STOCK_STATEMENT',
            menu_type = 'PAGE', is_active = TRUE, is_visible = TRUE
        WHERE menu_id = stock_id;
    END IF;

    -- Only view access is required; preserve any existing add/edit/delete settings.
    INSERT INTO role_permissions (role_id, menu_id, can_view, can_add, can_edit, can_delete)
    SELECT role_id, target.menu_id, TRUE, FALSE, FALSE, FALSE
    FROM roles CROSS JOIN (VALUES (credit_id), (reports_id), (stock_id)) AS target(menu_id)
    WHERE roles.is_active = TRUE AND UPPER(TRIM(roles.role_code)) IN ('SUPER_ADMIN', 'CREDIT')
    ON CONFLICT (role_id, menu_id) DO UPDATE SET can_view = TRUE;
END $$;
