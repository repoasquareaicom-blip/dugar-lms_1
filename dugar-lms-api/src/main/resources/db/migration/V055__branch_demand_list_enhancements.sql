INSERT INTO contract_flag_master (flag_code, flag_name, display_order, is_active, created_by)
VALUES
    ('REPOSSESSED_VEHICLE', 'Repossessed Vehicle', 8, TRUE, 'system'),
    ('LITIGATION', 'Litigation', 9, TRUE, 'system')
ON CONFLICT (flag_code) DO UPDATE
SET flag_name = EXCLUDED.flag_name,
    display_order = EXCLUDED.display_order,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system';

ALTER TABLE contract_follow_ups
    ADD COLUMN IF NOT EXISTS follow_up_type VARCHAR(20) NOT NULL DEFAULT 'COMMENT';

UPDATE contract_follow_ups
SET follow_up_type = 'COMMENT'
WHERE follow_up_type IS NULL
   OR TRIM(follow_up_type) = '';

ALTER TABLE contract_follow_ups
    DROP CONSTRAINT IF EXISTS chk_contract_follow_ups_type;

ALTER TABLE contract_follow_ups
    ADD CONSTRAINT chk_contract_follow_ups_type
    CHECK (UPPER(TRIM(follow_up_type)) IN ('COMMENT', 'PTP'));

CREATE INDEX IF NOT EXISTS idx_contract_follow_ups_type
    ON contract_follow_ups(UPPER(TRIM(follow_up_type)));

WITH branch_existing AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id IS NULL
      AND (
          menu_code = 'BRANCH'
          OR UPPER(TRIM(menu_name)) = 'BRANCH'
      )
    ORDER BY CASE WHEN menu_code = 'BRANCH' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
),
branch_insert AS (
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
        'Branch',
        NULL,
        '#',
        'GitBranch',
        3,
        'BRANCH',
        'MODULE',
        TRUE,
        TRUE
    WHERE NOT EXISTS (SELECT 1 FROM branch_existing)
    ON CONFLICT (menu_code) DO UPDATE
    SET menu_name = EXCLUDED.menu_name,
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
branch_menu AS (
    SELECT menu_id FROM branch_existing
    UNION ALL
    SELECT menu_id FROM branch_insert
    LIMIT 1
),
branch_pages AS (
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
    SELECT *
    FROM (
        SELECT 'Active Contracts' AS menu_name, branch_menu.menu_id AS parent_id, '/branch/active-contracts' AS url_path, 'Eye' AS icon, 1 AS display_order, 'BRANCH_ACTIVE_CONTRACTS' AS menu_code, 'PAGE' AS menu_type, TRUE AS is_active, TRUE AS is_visible FROM branch_menu
        UNION ALL
        SELECT 'Demand List', branch_menu.menu_id, '/branch/demand-list', 'HandCoins', 2, 'BRANCH_DEMAND_LIST', 'PAGE', TRUE, TRUE FROM branch_menu
        UNION ALL
        SELECT 'AFC Report', branch_menu.menu_id, '/branch/afc', 'ReceiptText', 3, 'BRANCH_AFC_REPORT', 'PAGE', TRUE, TRUE FROM branch_menu
        UNION ALL
        SELECT 'Ageing Analysis', branch_menu.menu_id, '/branch/aging-analysis', 'ChartNoAxesCombined', 4, 'BRANCH_AGING_ANALYSIS', 'PAGE', TRUE, TRUE FROM branch_menu
    ) pages
    ON CONFLICT (menu_code) DO UPDATE
    SET menu_name = EXCLUDED.menu_name,
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
          OR UPPER(role_code) = 'BRANCH'
          OR UPPER(role_name) = 'BRANCH'
      )
),
permission_menus AS (
    SELECT menu_id FROM branch_menu
    UNION
    SELECT menu_id FROM branch_pages
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
SET can_view = TRUE,
    can_add = FALSE,
    can_edit = FALSE,
    can_delete = FALSE,
    updated_at = CURRENT_TIMESTAMP;
