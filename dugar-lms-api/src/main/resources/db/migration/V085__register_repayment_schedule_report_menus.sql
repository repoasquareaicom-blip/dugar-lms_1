DO $$
DECLARE
    transactions_id BIGINT;
    active_contract_id BIGINT;
    last_order INTEGER;
BEGIN
    -- Generate Document already resolves to the existing Operations > Transactions tree.
    SELECT parent_id INTO STRICT transactions_id
    FROM menus WHERE menu_code = 'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT';
    SELECT menu_id INTO STRICT active_contract_id
    FROM menus WHERE menu_code = 'OPERATIONS_CONTRACT_MANAGEMENT_ACTIVE_CONTRACT';
    IF transactions_id IS NULL THEN
        RAISE EXCEPTION 'Operations Transactions menu is missing';
    END IF;
    SELECT COALESCE(MAX(display_order), 0) INTO last_order FROM menus WHERE parent_id = transactions_id;

    WITH report_menus AS (
        INSERT INTO menus (menu_name, parent_id, menu_code, url_path, menu_type, display_order, icon, is_active, is_visible)
        VALUES
            ('Repayment Schedule With Interest', transactions_id, 'OPERATIONS_TRANSACTIONS_REPAYMENT_SCHEDULE_WITH_INTEREST',
                '/operations/transactions/repayment-schedule-with-interest', 'PAGE', last_order + 1, 'FileText', TRUE, TRUE),
            ('Repayment Schedule Normal', transactions_id, 'OPERATIONS_TRANSACTIONS_REPAYMENT_SCHEDULE_NORMAL',
                '/operations/transactions/repayment-schedule-normal', 'PAGE', last_order + 2, 'FileText', TRUE, TRUE)
        ON CONFLICT (menu_code) DO UPDATE SET menu_name = EXCLUDED.menu_name, parent_id = EXCLUDED.parent_id,
            url_path = EXCLUDED.url_path, menu_type = EXCLUDED.menu_type, icon = EXCLUDED.icon,
            is_active = TRUE, is_visible = TRUE, updated_at = CURRENT_TIMESTAMP
        RETURNING menu_id
    )
    INSERT INTO role_permissions (role_id, menu_id, can_view, can_add, can_edit, can_delete)
    SELECT r.role_id, m.menu_id, TRUE, FALSE, FALSE, FALSE
    FROM role_permissions p JOIN roles r ON r.role_id = p.role_id
    CROSS JOIN report_menus m
    WHERE r.is_active AND p.can_view AND p.menu_id = active_contract_id
    ON CONFLICT (role_id, menu_id) DO UPDATE SET can_view = TRUE, updated_at = CURRENT_TIMESTAMP;
END $$;
