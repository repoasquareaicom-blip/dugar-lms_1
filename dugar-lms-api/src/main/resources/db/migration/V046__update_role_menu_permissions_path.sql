UPDATE menus
SET
    url_path = '/committee/masters/user-management/role-menu-permissions',
    updated_at = CURRENT_TIMESTAMP
WHERE UPPER(TRIM(menu_name)) = 'ROLE MENU PERMISSIONS'
  AND COALESCE(url_path, '#') = '#';
