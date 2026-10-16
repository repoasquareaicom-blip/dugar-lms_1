-- Reuse existing CREDIT/Reports containers and the existing role permission model.
INSERT INTO menus (menu_name, parent_id, url_path, icon, display_order, menu_code, menu_type, is_active, is_visible)
SELECT 'Reports', credit.menu_id, NULL, 'FileText', 3, 'CREDIT_REPORTS', 'MENU', TRUE, TRUE
FROM menus credit WHERE credit.menu_code = 'CREDIT'
AND NOT EXISTS (SELECT 1 FROM menus reports WHERE reports.parent_id = credit.menu_id
    AND (reports.menu_code = 'CREDIT_REPORTS' OR UPPER(TRIM(reports.menu_name)) = 'REPORTS'))
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO menus (menu_name, parent_id, url_path, icon, display_order, menu_code, menu_type, is_active, is_visible)
SELECT 'Stock Statement', reports.menu_id, '/credit/reports/stock-statement', 'FileSpreadsheet', 20,
    'CREDIT_REPORTS_STOCK_STATEMENT', 'PAGE', TRUE, TRUE
FROM menus reports JOIN menus credit ON credit.menu_id = reports.parent_id
WHERE credit.menu_code = 'CREDIT'
  AND (reports.menu_code = 'CREDIT_REPORTS' OR UPPER(TRIM(reports.menu_name)) = 'REPORTS')
ORDER BY CASE WHEN reports.menu_code = 'CREDIT_REPORTS' THEN 0 ELSE 1 END, reports.menu_id LIMIT 1
ON CONFLICT (menu_code) DO UPDATE SET parent_id = EXCLUDED.parent_id, url_path = EXCLUDED.url_path,
    is_active = TRUE, is_visible = TRUE;

-- Grant the new report only to active administrators; other roles can be assigned through Role Menu Permissions.
INSERT INTO role_permissions (role_id, menu_id, can_view, can_add, can_edit, can_delete)
SELECT roles.role_id, menus.menu_id, TRUE, FALSE, FALSE, FALSE
FROM roles CROSS JOIN menus
WHERE roles.is_active = TRUE AND UPPER(roles.role_code) IN ('SUPER_ADMIN', 'ADMIN')
  AND (menus.menu_code IN ('CREDIT', 'CREDIT_REPORTS_STOCK_STATEMENT')
      OR menus.menu_id = (SELECT parent_id FROM menus WHERE menu_code = 'CREDIT_REPORTS_STOCK_STATEMENT'))
ON CONFLICT (role_id, menu_id) DO NOTHING;
