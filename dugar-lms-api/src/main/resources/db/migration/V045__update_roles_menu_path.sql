UPDATE menus
SET
    url_path = '/committee/masters/user-management/roles',
    updated_at = CURRENT_TIMESTAMP
WHERE UPPER(TRIM(menu_name)) = 'ROLES'
  AND COALESCE(url_path, '#') = '#';
