CREATE TABLE IF NOT EXISTS word_template_groups (
    id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uq_word_template_groups_group_name UNIQUE (group_name)
);

CREATE TABLE IF NOT EXISTS word_templates (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    template_name VARCHAR(150) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_path TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_word_templates_group
        FOREIGN KEY (group_id)
        REFERENCES word_template_groups(id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT uq_word_templates_group_template_name UNIQUE (group_id, template_name)
);

CREATE INDEX IF NOT EXISTS idx_word_template_groups_active_order
    ON word_template_groups(is_active, display_order, group_name);

CREATE INDEX IF NOT EXISTS idx_word_templates_group_active_order
    ON word_templates(group_id, is_active, display_order, template_name);

CREATE OR REPLACE TRIGGER trg_word_template_groups_set_updated_at
    BEFORE UPDATE ON word_template_groups
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

CREATE OR REPLACE TRIGGER trg_word_templates_set_updated_at
    BEFORE UPDATE ON word_templates
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

WITH administration_existing AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id IS NULL
      AND (
          menu_code = 'ADMINISTRATION'
          OR UPPER(TRIM(menu_name)) = 'ADMINISTRATION'
      )
    ORDER BY CASE WHEN menu_code = 'ADMINISTRATION' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
),
administration_insert AS (
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
        'Administration',
        NULL,
        '#',
        'Settings',
        90,
        'ADMINISTRATION',
        'MODULE',
        TRUE,
        TRUE
    WHERE NOT EXISTS (SELECT 1 FROM administration_existing)
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
administration AS (
    SELECT menu_id FROM administration_existing
    UNION ALL
    SELECT menu_id FROM administration_insert
    LIMIT 1
),
administration_masters AS (
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
        administration.menu_id,
        '#',
        'Database',
        1,
        'ADMINISTRATION_MASTERS',
        'MENU',
        TRUE,
        TRUE
    FROM administration
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
word_template_master AS (
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
        'Word Template Master',
        administration_masters.menu_id,
        '/administration/masters/word-template-master',
        'FileText',
        1,
        'ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER',
        'PAGE',
        TRUE,
        TRUE
    FROM administration_masters
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
super_admin_roles AS (
    SELECT role_id
    FROM roles
    WHERE is_active = TRUE
      AND (
          UPPER(TRIM(role_name)) = 'SUPER_ADMIN'
          OR UPPER(TRIM(role_code)) LIKE 'SUPER_ADMIN%'
      )
),
permission_menus AS (
    SELECT menu_id FROM administration
    UNION
    SELECT menu_id FROM administration_masters
    UNION
    SELECT menu_id FROM word_template_master
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
    super_admin_roles.role_id,
    permission_menus.menu_id,
    TRUE,
    FALSE,
    FALSE,
    FALSE
FROM super_admin_roles
CROSS JOIN permission_menus
ON CONFLICT (role_id, menu_id) DO UPDATE
SET
    can_view = TRUE,
    updated_at = CURRENT_TIMESTAMP;

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
transactions_existing AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id = (SELECT menu_id FROM operations)
      AND (
          menu_code IN ('OPERATIONS_TRANSACTIONS', 'OPERATIONS_TRANSACTION')
          OR UPPER(TRIM(menu_name)) IN ('TRANSACTIONS', 'TRANSACTION')
      )
    ORDER BY
        CASE
            WHEN menu_code = 'OPERATIONS_TRANSACTIONS' THEN 0
            WHEN menu_code = 'OPERATIONS_TRANSACTION' THEN 1
            ELSE 2
        END,
        menu_id
    LIMIT 1
),
transactions_insert AS (
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
        'Transactions',
        operations.menu_id,
        '#',
        'ArrowLeftRight',
        1,
        'OPERATIONS_TRANSACTIONS',
        'MENU',
        TRUE,
        TRUE
    FROM operations
    WHERE NOT EXISTS (SELECT 1 FROM transactions_existing)
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
transactions AS (
    SELECT menu_id FROM transactions_existing
    UNION ALL
    SELECT menu_id FROM transactions_insert
    LIMIT 1
),
generate_word_document AS (
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
        'Generate Word Document',
        transactions.menu_id,
        '/operations/transactions/generate-word-document',
        'FilePlus2',
        50,
        'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT',
        'PAGE',
        TRUE,
        TRUE
    FROM transactions
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
super_admin_roles AS (
    SELECT role_id
    FROM roles
    WHERE is_active = TRUE
      AND (
          UPPER(TRIM(role_name)) = 'SUPER_ADMIN'
          OR UPPER(TRIM(role_code)) LIKE 'SUPER_ADMIN%'
      )
),
permission_menus AS (
    SELECT menu_id FROM operations
    UNION
    SELECT menu_id FROM transactions
    UNION
    SELECT menu_id FROM generate_word_document
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
    super_admin_roles.role_id,
    permission_menus.menu_id,
    TRUE,
    FALSE,
    FALSE,
    FALSE
FROM super_admin_roles
CROSS JOIN permission_menus
ON CONFLICT (role_id, menu_id) DO UPDATE
SET
    can_view = TRUE,
    updated_at = CURRENT_TIMESTAMP;
