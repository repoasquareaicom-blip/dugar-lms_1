WITH committee AS (
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
    VALUES (
        'Committee',
        NULL,
        '#',
        'UsersRound',
        6,
        'COMMITTEE',
        'MODULE',
        TRUE,
        TRUE
    )
    ON CONFLICT (menu_code) DO UPDATE
    SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        url_path = EXCLUDED.url_path,
        icon = EXCLUDED.icon,
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING menu_id
),
reports AS (
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
        'Reports',
        committee.menu_id,
        '#',
        'FileBarChart2',
        1,
        'COMMITTEE_REPORTS',
        'MENU',
        TRUE,
        TRUE
    FROM committee
    ON CONFLICT (menu_code) DO UPDATE
    SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        url_path = EXCLUDED.url_path,
        icon = EXCLUDED.icon,
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING menu_id
),
demand_list AS (
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
        'Demand List',
        reports.menu_id,
        '/committee/reports/demand-list',
        'FileSpreadsheet',
        1,
        'COMMITTEE_DEMAND_LIST',
        'PAGE',
        TRUE,
        TRUE
    FROM reports
    ON CONFLICT (menu_code) DO UPDATE
    SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        url_path = EXCLUDED.url_path,
        icon = EXCLUDED.icon,
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING menu_id
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
    roles.role_id,
    menus.menu_id,
    TRUE,
    FALSE,
    FALSE,
    FALSE
FROM roles
JOIN menus
  ON menus.menu_code IN ('COMMITTEE', 'COMMITTEE_REPORTS', 'COMMITTEE_DEMAND_LIST')
WHERE roles.is_active = TRUE
  AND (
      UPPER(roles.role_name) IN ('SUPER_ADMIN', 'ADMIN')
      OR UPPER(roles.role_code) LIKE 'SUPER_ADMIN%'
      OR UPPER(roles.role_code) LIKE 'ADMIN%'
  )
ON CONFLICT (role_id, menu_id) DO UPDATE
SET
    can_view = TRUE,
    updated_at = CURRENT_TIMESTAMP;
