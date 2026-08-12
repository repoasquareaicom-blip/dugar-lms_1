WITH aging_analysis AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'COMMITTEE_AGING_ANALYSIS'
),
loan_ticket_wise AS (
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
        'Loan Ticket Wise',
        aging_analysis.menu_id,
        '/committee/aging-analysis/loan-ticket-wise',
        'IndianRupee',
        3,
        'COMMITTEE_AGING_LOAN_TICKET_WISE',
        'PAGE',
        TRUE,
        TRUE
    FROM aging_analysis
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
interest_wise AS (
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
        'Interest Wise',
        aging_analysis.menu_id,
        '/committee/aging-analysis/interest-wise',
        'Percent',
        4,
        'COMMITTEE_AGING_INTEREST_WISE',
        'PAGE',
        TRUE,
        TRUE
    FROM aging_analysis
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
target_menus AS (
    SELECT menu_id FROM loan_ticket_wise
    UNION ALL
    SELECT menu_id FROM interest_wise
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
    target_menus.menu_id,
    TRUE,
    FALSE,
    FALSE,
    FALSE
FROM roles
CROSS JOIN target_menus
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
