WITH branch_menu AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id IS NULL
      AND (
          menu_code IN ('BRANCH', 'BRANCH_216')
          OR UPPER(TRIM(menu_name)) = 'BRANCH'
      )
    ORDER BY
        CASE
            WHEN menu_code = 'BRANCH' THEN 0
            WHEN menu_code = 'BRANCH_216' THEN 1
            ELSE 2
        END,
        menu_id
    LIMIT 1
),
branch_transaction AS (
    SELECT menus.menu_id
    FROM menus
    JOIN branch_menu ON branch_menu.menu_id = menus.parent_id
    WHERE (
        menus.menu_code IN ('BRANCH_TRANSACTION', 'TRANSACTION_217')
        OR UPPER(TRIM(menus.menu_name)) = 'TRANSACTION'
    )
    ORDER BY
        CASE
            WHEN menus.menu_code = 'BRANCH_TRANSACTION' THEN 0
            WHEN menus.menu_code = 'TRANSACTION_217' THEN 1
            ELSE 2
        END,
        menus.menu_id
    LIMIT 1
),
branch_reports AS (
    SELECT menus.menu_id
    FROM menus
    JOIN branch_menu ON branch_menu.menu_id = menus.parent_id
    WHERE (
        menus.menu_code IN ('BRANCH_REPORTS', 'REPORTS_227')
        OR UPPER(TRIM(menus.menu_name)) = 'REPORTS'
    )
    ORDER BY
        CASE
            WHEN menus.menu_code = 'BRANCH_REPORTS' THEN 0
            WHEN menus.menu_code = 'REPORTS_227' THEN 1
            ELSE 2
        END,
        menus.menu_id
    LIMIT 1
)
UPDATE menus
SET
    parent_id = branch_transaction.menu_id,
    display_order = 1,
    updated_at = CURRENT_TIMESTAMP
FROM branch_transaction
WHERE menus.menu_code = 'BRANCH_ACTIVE_CONTRACTS';

WITH branch_menu AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id IS NULL
      AND (
          menu_code IN ('BRANCH', 'BRANCH_216')
          OR UPPER(TRIM(menu_name)) = 'BRANCH'
      )
    ORDER BY
        CASE
            WHEN menu_code = 'BRANCH' THEN 0
            WHEN menu_code = 'BRANCH_216' THEN 1
            ELSE 2
        END,
        menu_id
    LIMIT 1
),
branch_transaction AS (
    SELECT menus.menu_id
    FROM menus
    JOIN branch_menu ON branch_menu.menu_id = menus.parent_id
    WHERE (
        menus.menu_code IN ('BRANCH_TRANSACTION', 'TRANSACTION_217')
        OR UPPER(TRIM(menus.menu_name)) = 'TRANSACTION'
    )
    ORDER BY
        CASE
            WHEN menus.menu_code = 'BRANCH_TRANSACTION' THEN 0
            WHEN menus.menu_code = 'TRANSACTION_217' THEN 1
            ELSE 2
        END,
        menus.menu_id
    LIMIT 1
)
UPDATE menus
SET
    display_order = CASE menus.menu_code
        WHEN 'CREATE_LEAD_WILL_TAKE_HIM_TO_LOS_218' THEN 2
        WHEN 'REQUEST_TO_FLAG_LOAN_219' THEN 3
        ELSE menus.display_order
    END,
    updated_at = CURRENT_TIMESTAMP
FROM branch_transaction
WHERE menus.parent_id = branch_transaction.menu_id
  AND menus.menu_code IN (
      'CREATE_LEAD_WILL_TAKE_HIM_TO_LOS_218',
      'REQUEST_TO_FLAG_LOAN_219'
  );

WITH branch_menu AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id IS NULL
      AND (
          menu_code IN ('BRANCH', 'BRANCH_216')
          OR UPPER(TRIM(menu_name)) = 'BRANCH'
      )
    ORDER BY
        CASE
            WHEN menu_code = 'BRANCH' THEN 0
            WHEN menu_code = 'BRANCH_216' THEN 1
            ELSE 2
        END,
        menu_id
    LIMIT 1
),
branch_reports AS (
    SELECT menus.menu_id
    FROM menus
    JOIN branch_menu ON branch_menu.menu_id = menus.parent_id
    WHERE (
        menus.menu_code IN ('BRANCH_REPORTS', 'REPORTS_227')
        OR UPPER(TRIM(menus.menu_name)) = 'REPORTS'
    )
    ORDER BY
        CASE
            WHEN menus.menu_code = 'BRANCH_REPORTS' THEN 0
            WHEN menus.menu_code = 'REPORTS_227' THEN 1
            ELSE 2
        END,
        menus.menu_id
    LIMIT 1
)
UPDATE menus
SET
    display_order = display_order + 10,
    updated_at = CURRENT_TIMESTAMP
FROM branch_reports
WHERE menus.parent_id = branch_reports.menu_id
  AND menus.menu_code NOT IN (
      'BRANCH_DEMAND_LIST',
      'BRANCH_AFC_REPORT',
      'BRANCH_AGING_ANALYSIS'
  )
  AND menus.display_order < 10;

WITH branch_menu AS (
    SELECT menu_id
    FROM menus
    WHERE parent_id IS NULL
      AND (
          menu_code IN ('BRANCH', 'BRANCH_216')
          OR UPPER(TRIM(menu_name)) = 'BRANCH'
      )
    ORDER BY
        CASE
            WHEN menu_code = 'BRANCH' THEN 0
            WHEN menu_code = 'BRANCH_216' THEN 1
            ELSE 2
        END,
        menu_id
    LIMIT 1
),
branch_reports AS (
    SELECT menus.menu_id
    FROM menus
    JOIN branch_menu ON branch_menu.menu_id = menus.parent_id
    WHERE (
        menus.menu_code IN ('BRANCH_REPORTS', 'REPORTS_227')
        OR UPPER(TRIM(menus.menu_name)) = 'REPORTS'
    )
    ORDER BY
        CASE
            WHEN menus.menu_code = 'BRANCH_REPORTS' THEN 0
            WHEN menus.menu_code = 'REPORTS_227' THEN 1
            ELSE 2
        END,
        menus.menu_id
    LIMIT 1
)
UPDATE menus
SET
    parent_id = branch_reports.menu_id,
    display_order = CASE menus.menu_code
        WHEN 'BRANCH_DEMAND_LIST' THEN 1
        WHEN 'BRANCH_AFC_REPORT' THEN 2
        WHEN 'BRANCH_AGING_ANALYSIS' THEN 3
        ELSE menus.display_order
    END,
    updated_at = CURRENT_TIMESTAMP
FROM branch_reports
WHERE menus.menu_code IN (
    'BRANCH_DEMAND_LIST',
    'BRANCH_AFC_REPORT',
    'BRANCH_AGING_ANALYSIS'
);
