UPDATE menus
SET
    is_active = FALSE,
    is_visible = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'VOUCHER_REQUEST_SENT_FOR_AUTHORIZATION'
   OR (
       LOWER(TRIM(menu_name)) = 'voucher request sent for authorization'
       AND COALESCE(url_path, '') = '/accounts/transaction/voucher-authorisation'
   );
