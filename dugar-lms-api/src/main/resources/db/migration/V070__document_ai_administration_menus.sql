DO $$
DECLARE admin_id BIGINT; reports_id BIGINT; masters_id BIGINT; report_id BIGINT; settings_id BIGINT;
BEGIN
    SELECT parent_id INTO STRICT masters_id FROM menus WHERE menu_code='ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER';
    SELECT parent_id INTO STRICT admin_id FROM menus WHERE menu_id=masters_id;
    IF admin_id IS NULL THEN RAISE EXCEPTION 'Document AI Administration root is missing'; END IF;
    SELECT menu_id INTO reports_id FROM menus WHERE parent_id=admin_id AND menu_code='ADMINISTRATION_REPORTS';
    IF reports_id IS NULL THEN
        SELECT menu_id INTO reports_id FROM menus WHERE parent_id=admin_id AND UPPER(TRIM(menu_name))='REPORTS';
        IF (SELECT COUNT(*) FROM menus WHERE parent_id=admin_id AND UPPER(TRIM(menu_name))='REPORTS') > 1 THEN
            RAISE EXCEPTION 'Ambiguous Administration Reports menus';
        END IF;
    END IF;
    IF reports_id IS NULL THEN
        INSERT INTO menus(menu_name,parent_id,menu_code,url_path,menu_type,display_order)
        VALUES('Reports',admin_id,'ADMINISTRATION_REPORTS','#','MENU',3) RETURNING menu_id INTO reports_id;
    END IF;
    INSERT INTO menus(menu_name,parent_id,menu_code,url_path,menu_type,display_order,icon)
    VALUES('AI API Usage & Billing',reports_id,'ADMINISTRATION_REPORTS_AI_USAGE_BILLING',
        '/administration/reports/ai-usage-billing','PAGE',1,'ChartNoAxesCombined')
    ON CONFLICT(menu_code) DO UPDATE SET menu_name=EXCLUDED.menu_name,parent_id=EXCLUDED.parent_id,
        is_active=TRUE,is_visible=TRUE RETURNING menu_id INTO report_id;
    INSERT INTO menus(menu_name,parent_id,menu_code,url_path,menu_type,display_order,icon)
    VALUES('Document AI Settings',masters_id,'ADMINISTRATION_MASTERS_DOCUMENT_AI_SETTINGS',
        '/administration/masters/document-ai-settings','PAGE',2,'Settings')
    ON CONFLICT(menu_code) DO UPDATE SET parent_id=EXCLUDED.parent_id,is_active=TRUE,is_visible=TRUE
    RETURNING menu_id INTO settings_id;
    UPDATE menus SET is_active=TRUE,is_visible=TRUE WHERE menu_id IN(admin_id,masters_id,reports_id);
    INSERT INTO role_permissions(role_id,menu_id,can_view)
    SELECT role_id,m.id,TRUE FROM roles CROSS JOIN (VALUES(admin_id),(masters_id),(reports_id),(report_id),(settings_id)) m(id)
    WHERE is_active AND (UPPER(TRIM(role_code))='SUPER_ADMIN' OR UPPER(TRIM(role_name))='SUPER_ADMIN')
    ON CONFLICT(role_id,menu_id) DO UPDATE SET can_view=TRUE;
END $$;
