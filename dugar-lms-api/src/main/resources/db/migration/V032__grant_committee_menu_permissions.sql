WITH RECURSIVE committee_tree AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'COMMITTEE'

    UNION ALL

    SELECT child.menu_id
    FROM menus child
    JOIN committee_tree parent
      ON child.parent_id = parent.menu_id
    WHERE child.is_active = TRUE
      AND child.is_visible = TRUE
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
    committee_tree.menu_id,
    TRUE,
    FALSE,
    FALSE,
    FALSE
FROM roles
CROSS JOIN committee_tree
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
