WITH super_admin_role AS (
    INSERT INTO roles (
        role_name,
        role_code,
        is_active
    )
    VALUES (
        'SUPER_ADMIN',
        'SUPER_ADMIN',
        TRUE
    )
    ON CONFLICT (role_code) DO UPDATE
    SET
        role_name = EXCLUDED.role_name,
        is_active = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING role_id
),
required_role_codes(role_code) AS (
    VALUES
        ('CREDIT'),
        ('OPERATIONS'),
        ('ACCOUNTS'),
        ('LEGAL'),
        ('COLLECTION'),
        ('COMMITTEE'),
        ('BRANCH'),
        ('CUSTOMER')
),
administrator_users AS (
    SELECT user_roles.user_id
    FROM user_roles
    JOIN roles
      ON roles.role_id = user_roles.role_id
     AND roles.is_active = TRUE
    JOIN users
      ON users.user_id = user_roles.user_id
     AND users.is_active = TRUE
    JOIN required_role_codes
      ON required_role_codes.role_code = UPPER(TRIM(roles.role_code))
    GROUP BY user_roles.user_id
    HAVING COUNT(DISTINCT UPPER(TRIM(roles.role_code))) = (SELECT COUNT(*) FROM required_role_codes)
),
assigned_super_admin AS (
    INSERT INTO user_roles (
        user_id,
        role_id
    )
    SELECT
        administrator_users.user_id,
        super_admin_role.role_id
    FROM administrator_users
    CROSS JOIN super_admin_role
    ON CONFLICT (user_id, role_id) DO NOTHING
    RETURNING user_id
),
primary_role_update AS (
    UPDATE users
    SET
        role_id = super_admin_role.role_id,
        updated_at = CURRENT_TIMESTAMP
    FROM administrator_users
    CROSS JOIN super_admin_role
    WHERE users.user_id = administrator_users.user_id
    RETURNING users.user_id
),
active_menus AS (
    SELECT menu_id
    FROM menus
    WHERE is_active = TRUE
      AND is_visible = TRUE
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
    super_admin_role.role_id,
    active_menus.menu_id,
    TRUE,
    FALSE,
    FALSE,
    FALSE
FROM super_admin_role
CROSS JOIN active_menus
ON CONFLICT (role_id, menu_id) DO UPDATE
SET
    can_view = TRUE,
    updated_at = CURRENT_TIMESTAMP;

WITH super_admin_role AS (
    SELECT role_id
    FROM roles
    WHERE UPPER(TRIM(role_code)) = 'SUPER_ADMIN'
),
word_template_master AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER'
)
UPDATE role_permissions
SET
    can_view = FALSE,
    updated_at = CURRENT_TIMESTAMP
FROM word_template_master
WHERE role_permissions.menu_id = word_template_master.menu_id
  AND role_permissions.role_id NOT IN (SELECT role_id FROM super_admin_role);
