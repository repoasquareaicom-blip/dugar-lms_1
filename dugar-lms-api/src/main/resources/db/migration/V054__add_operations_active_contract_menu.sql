WITH operations_existing AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id IS NULL
      AND (
          menu_code = 'OPERATIONS'
          OR UPPER(TRIM(menu_name)) = 'OPERATIONS'
      )
    ORDER BY CASE WHEN menu_code = 'OPERATIONS' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
),
operations_insert AS (
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
        'Operations',
        NULL,
        '#',
        'BriefcaseBusiness',
        2,
        'OPERATIONS',
        'MODULE',
        TRUE,
        TRUE
    WHERE NOT EXISTS (SELECT 1 FROM operations_existing)
    ON CONFLICT (menu_code) DO UPDATE
    SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        url_path = EXCLUDED.url_path,
        icon = EXCLUDED.icon,
        menu_type = EXCLUDED.menu_type,
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING menu_id
),
operations AS (
    SELECT menu_id FROM operations_existing
    UNION ALL
    SELECT menu_id FROM operations_insert
    LIMIT 1
),
transaction_existing AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id = (SELECT menu_id FROM operations)
      AND (
          menu_code = 'OPERATIONS_TRANSACTION'
          OR UPPER(TRIM(menu_name)) = 'TRANSACTION'
      )
    ORDER BY CASE WHEN menu_code = 'OPERATIONS_TRANSACTION' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
),
transaction_insert AS (
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
        'Transaction',
        operations.menu_id,
        '#',
        'ArrowLeftRight',
        1,
        'OPERATIONS_TRANSACTION',
        'MENU',
        TRUE,
        TRUE
    FROM operations
    WHERE NOT EXISTS (SELECT 1 FROM transaction_existing)
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
transaction_menu AS (
    SELECT menu_id FROM transaction_existing
    UNION ALL
    SELECT menu_id FROM transaction_insert
    LIMIT 1
),
contract_management_existing AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id = (SELECT menu_id FROM transaction_menu)
      AND (
          menu_code = 'OPERATIONS_CONTRACT_MANAGEMENT'
          OR UPPER(TRIM(menu_name)) = 'CONTRACT MANAGEMENT'
      )
    ORDER BY CASE WHEN menu_code = 'OPERATIONS_CONTRACT_MANAGEMENT' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
),
contract_management_insert AS (
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
        'Contract Management',
        transaction_menu.menu_id,
        '#',
        'FileSpreadsheet',
        1,
        'OPERATIONS_CONTRACT_MANAGEMENT',
        'MENU',
        TRUE,
        TRUE
    FROM transaction_menu
    WHERE NOT EXISTS (SELECT 1 FROM contract_management_existing)
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
contract_management AS (
    SELECT menu_id FROM contract_management_existing
    UNION ALL
    SELECT menu_id FROM contract_management_insert
    LIMIT 1
),
active_contract AS (
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
        'Active Contract',
        contract_management.menu_id,
        '/credit/trans/contract-management/active-contracts',
        'CheckCircle',
        1,
        'OPERATIONS_CONTRACT_MANAGEMENT_ACTIVE_CONTRACT',
        'PAGE',
        TRUE,
        TRUE
    FROM contract_management
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
eligible_roles AS (
    SELECT role_id
    FROM roles
    WHERE is_active = TRUE
      AND (
          UPPER(role_name) = 'SUPER_ADMIN'
          OR UPPER(role_name) = 'ADMIN'
          OR UPPER(role_code) LIKE 'SUPER_ADMIN%'
          OR UPPER(role_code) LIKE 'ADMIN%'
      )

    UNION

    SELECT rp.role_id
    FROM role_permissions rp
    WHERE rp.can_view = TRUE
      AND rp.menu_id IN (
          SELECT menu_id FROM operations
          UNION
          SELECT menu_id FROM transaction_menu
          UNION
          SELECT menu_id FROM contract_management
      )
),
permission_menus AS (
    SELECT menu_id FROM operations
    UNION
    SELECT menu_id FROM transaction_menu
    UNION
    SELECT menu_id FROM contract_management
    UNION
    SELECT menu_id FROM active_contract
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
    eligible_roles.role_id,
    permission_menus.menu_id,
    TRUE,
    FALSE,
    FALSE,
    FALSE
FROM eligible_roles
CROSS JOIN permission_menus
ON CONFLICT (role_id, menu_id) DO UPDATE
SET
    can_view = TRUE,
    updated_at = CURRENT_TIMESTAMP;
