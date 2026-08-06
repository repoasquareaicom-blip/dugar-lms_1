WITH canonical_committee AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'COMMITTEE'
    LIMIT 1
),
legacy_committee AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code <> 'COMMITTEE'
      AND UPPER(TRIM(menu_name)) = 'COMMITTEE'
      AND menu_code LIKE 'COMMITTEE_%'
    ORDER BY menu_id
    LIMIT 1
),
legacy_reports AS (
    SELECT menu_id, display_order
    FROM menus
    WHERE parent_id = (SELECT menu_id FROM legacy_committee)
      AND UPPER(TRIM(menu_name)) = 'REPORTS'
    ORDER BY menu_id
    LIMIT 1
),
canonical_reports AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'COMMITTEE_REPORTS'
    LIMIT 1
)
UPDATE menus child
SET
    parent_id = (SELECT menu_id FROM canonical_committee),
    updated_at = CURRENT_TIMESTAMP
WHERE child.parent_id = (SELECT menu_id FROM legacy_committee)
  AND child.menu_id <> COALESCE((SELECT menu_id FROM legacy_reports), -1)
  AND EXISTS (SELECT 1 FROM canonical_committee)
  AND EXISTS (SELECT 1 FROM legacy_committee);

WITH legacy_reports AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code LIKE 'REPORTS_%'
      AND UPPER(TRIM(menu_name)) = 'REPORTS'
      AND parent_id IN (
          SELECT menu_id
          FROM menus
          WHERE menu_code LIKE 'COMMITTEE_%'
            AND UPPER(TRIM(menu_name)) = 'COMMITTEE'
      )
    ORDER BY menu_id
    LIMIT 1
),
canonical_reports AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'COMMITTEE_REPORTS'
    LIMIT 1
)
UPDATE menus report_child
SET
    parent_id = (SELECT menu_id FROM canonical_reports),
    updated_at = CURRENT_TIMESTAMP
WHERE report_child.parent_id = (SELECT menu_id FROM legacy_reports)
  AND EXISTS (SELECT 1 FROM canonical_reports)
  AND EXISTS (SELECT 1 FROM legacy_reports);

WITH legacy_reports AS (
    SELECT menu_id, display_order
    FROM menus
    WHERE menu_code LIKE 'REPORTS_%'
      AND UPPER(TRIM(menu_name)) = 'REPORTS'
    ORDER BY menu_id
    LIMIT 1
)
UPDATE menus
SET
    display_order = COALESCE((SELECT display_order FROM legacy_reports), display_order),
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'COMMITTEE_REPORTS';

WITH max_report_order AS (
    SELECT COALESCE(MAX(display_order), 0) AS max_order
    FROM menus
    WHERE parent_id = (
        SELECT menu_id
        FROM menus
        WHERE menu_code = 'COMMITTEE_REPORTS'
        LIMIT 1
    )
      AND menu_code NOT IN ('COMMITTEE_DEMAND_LIST', 'COMMITTEE_AFC_REPORT')
)
UPDATE menus
SET
    display_order = CASE menu_code
        WHEN 'COMMITTEE_DEMAND_LIST' THEN (SELECT max_order + 1 FROM max_report_order)
        WHEN 'COMMITTEE_AFC_REPORT' THEN (SELECT max_order + 2 FROM max_report_order)
        ELSE display_order
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code IN ('COMMITTEE_DEMAND_LIST', 'COMMITTEE_AFC_REPORT');

UPDATE menus
SET
    is_active = FALSE,
    is_visible = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE (
        menu_code LIKE 'COMMITTEE_%'
        AND menu_code <> 'COMMITTEE_REPORTS'
        AND UPPER(TRIM(menu_name)) = 'COMMITTEE'
    )
   OR (
        menu_code LIKE 'REPORTS_%'
        AND UPPER(TRIM(menu_name)) = 'REPORTS'
    );

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
    COALESCE(existing.can_add, FALSE),
    COALESCE(existing.can_edit, FALSE),
    COALESCE(existing.can_delete, FALSE)
FROM roles
CROSS JOIN committee_tree
LEFT JOIN role_permissions existing
  ON existing.role_id = roles.role_id
 AND existing.menu_id = committee_tree.menu_id
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
