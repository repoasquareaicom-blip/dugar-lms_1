-- Backend properties now supply pricing. Keep historical pricing, usage and permissions for audit.
-- Only retire this page; Document Template Master and the Administrator billing report are unchanged.
UPDATE menus
SET is_active=FALSE, is_visible=FALSE
WHERE menu_code='ADMINISTRATION_MASTERS_DOCUMENT_AI_SETTINGS';
