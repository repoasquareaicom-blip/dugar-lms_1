CREATE TABLE IF NOT EXISTS bank_masters (
    id BIGSERIAL PRIMARY KEY,
    bank_code VARCHAR(30) UNIQUE NOT NULL,
    bank_name VARCHAR(150) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bank_masters_active
    ON bank_masters(is_active);

CREATE OR REPLACE TRIGGER trg_bank_masters_set_updated_at
    BEFORE UPDATE ON bank_masters
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

WITH credit_existing AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'CREDIT'
       OR UPPER(TRIM(menu_name)) = 'CREDIT'
    ORDER BY CASE WHEN menu_code = 'CREDIT' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
),
credit_insert AS (
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
        'CREDIT',
        NULL,
        NULL,
        'Wallet',
        1,
        'CREDIT',
        'MODULE',
        TRUE,
        TRUE
    WHERE NOT EXISTS (SELECT 1 FROM credit_existing)
    ON CONFLICT (menu_code) DO UPDATE
    SET menu_name = EXCLUDED.menu_name,
        menu_type = EXCLUDED.menu_type,
        is_active = TRUE,
        is_visible = TRUE
    RETURNING menu_id
),
credit AS (
    SELECT menu_id FROM credit_existing
    UNION ALL
    SELECT menu_id FROM credit_insert
),
masters_existing AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id = (SELECT menu_id FROM credit LIMIT 1)
      AND (
          menu_code = 'CREDIT_MASTERS'
          OR UPPER(TRIM(menu_name)) = 'MASTERS'
      )
    ORDER BY CASE WHEN menu_code = 'CREDIT_MASTERS' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
),
masters_insert AS (
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
        'Masters',
        credit.menu_id,
        NULL,
        'Settings',
        1,
        'CREDIT_MASTERS',
        'MENU',
        TRUE,
        TRUE
    FROM credit
    WHERE NOT EXISTS (SELECT 1 FROM masters_existing)
    ON CONFLICT (menu_code) DO UPDATE
    SET menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        menu_type = EXCLUDED.menu_type,
        is_active = TRUE,
        is_visible = TRUE
    RETURNING menu_id
),
masters AS (
    SELECT menu_id FROM masters_existing
    UNION ALL
    SELECT menu_id FROM masters_insert
),
bank_code_menu AS (
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
        'Bank Code',
        masters.menu_id,
        '/credit/masters/bank-code',
        'Landmark',
        20,
        'CREDIT_MASTERS_BANK_CODE',
        'PAGE',
        TRUE,
        TRUE
    FROM masters
    ON CONFLICT (menu_code) DO UPDATE
    SET menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        url_path = EXCLUDED.url_path,
        icon = EXCLUDED.icon,
        display_order = EXCLUDED.display_order,
        menu_type = EXCLUDED.menu_type,
        is_active = TRUE,
        is_visible = TRUE
    RETURNING menu_id
),
permission_menus AS (
    SELECT menu_id FROM credit
    UNION
    SELECT menu_id FROM masters
    UNION
    SELECT menu_id FROM bank_code_menu
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
    permission_menus.menu_id,
    TRUE,
    TRUE,
    TRUE,
    FALSE
FROM roles
CROSS JOIN permission_menus
WHERE roles.is_active = TRUE
ON CONFLICT (role_id, menu_id) DO UPDATE
SET can_view = TRUE,
    can_add = TRUE,
    can_edit = TRUE;
