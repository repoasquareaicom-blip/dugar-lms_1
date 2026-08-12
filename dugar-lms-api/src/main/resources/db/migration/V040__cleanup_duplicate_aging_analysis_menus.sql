WITH canonical_aging AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'COMMITTEE_AGING_ANALYSIS'
),
reports AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'COMMITTEE_REPORTS'
)
UPDATE menus
SET
    is_visible = FALSE,
    is_active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE parent_id = (SELECT menu_id FROM reports)
  AND menu_id <> (SELECT menu_id FROM canonical_aging)
  AND UPPER(TRIM(menu_name)) IN ('AGING ANALYSIS', 'AGEING ANALYSIS')
  AND NOT EXISTS (
      SELECT 1
      FROM menus child
      WHERE child.parent_id = menus.menu_id
  );
