-- Post-migration, read-only inspection. Select only a local/sanitized database.
BEGIN TRANSACTION READ ONLY;
SELECT current_database(),inet_server_addr(),inet_server_port();
SELECT installed_rank,version,description,success FROM flyway_schema_history
WHERE version IN ('060','061','062','063','067','068','069','070','071','072','073','074','075','076','077') ORDER BY installed_rank;
SELECT id,document_key,template_name,language_code,is_active,display_order,original_filename
FROM word_templates ORDER BY document_key,language_code;
-- Expected zero rows: no ambiguous active language variants.
SELECT LOWER(BTRIM(document_key)),language_code,COUNT(*) FROM word_templates
WHERE is_active GROUP BY LOWER(BTRIM(document_key)),language_code HAVING COUNT(*)>1;
-- Expected zero rows: alias collisions with configured canonical rules.
-- Runtime validation additionally checks the complete schema-driven registry.
SELECT a.alias,a.canonical_code FROM document_placeholder_aliases a
JOIN document_field_rules r ON LOWER(a.alias)=LOWER(TRIM(BOTH '{}' FROM r.canonical_code));
SELECT r.role_code,m.menu_code,m.menu_name,m.url_path,p.can_view,p.can_add,p.can_edit,p.can_delete
FROM menus m LEFT JOIN role_permissions p USING(menu_id) LEFT JOIN roles r USING(role_id)
WHERE m.menu_code IN ('ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER','OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT',
    'ADMINISTRATION_REPORTS_AI_USAGE_BILLING','ADMINISTRATION_MASTERS_DOCUMENT_AI_SETTINGS') ORDER BY m.menu_code,r.role_code;
SELECT id,model,input_per_million,cached_input_per_million,output_per_million,usd_inr,multiplier,effective_at,is_active
FROM ai_pricing_versions ORDER BY effective_at DESC,id DESC;
SELECT status,language_code,COUNT(*) AS attempts,SUM(cache_hits) AS cache_hits,SUM(cache_misses) AS cache_misses
FROM document_generation_audit GROUP BY status,language_code;
SELECT status,COUNT(*) AS requests,SUM(input_tokens) AS input_tokens,SUM(cached_input_tokens) AS cached_tokens,
    SUM(output_tokens) AS output_tokens,SUM(estimated_inr) AS estimated_cost_inr,SUM(billable_inr) AS billable_inr,
    COUNT(*) FILTER(WHERE estimated_inr IS NULL) AS unknown_cost_requests FROM ai_api_usage GROUP BY status;
-- Expected zero rows: shared request allocations must sum to exactly one.
SELECT usage_id,SUM(allocation) FROM document_generation_api_links GROUP BY usage_id HAVING SUM(allocation)<>1;
-- Expected zero rows: applied historical amounts must equal their stored snapshot calculation.
SELECT id FROM ai_api_usage WHERE estimated_inr IS NOT NULL AND
    (ABS(estimated_inr-estimated_usd*usd_inr)>0.00000000001 OR ABS(billable_inr-estimated_inr*multiplier)>0.00000000001);
SELECT generation_id,canonical_code,cache_id,cache_version FROM document_generation_translation_versions ORDER BY generation_id,canonical_code;
COMMIT;
