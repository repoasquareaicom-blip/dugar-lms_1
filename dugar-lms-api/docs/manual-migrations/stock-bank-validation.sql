-- Read-only checks AFTER stock-bank-id.sql.
SELECT COUNT(*) AS total_details,
 COUNT(*) FILTER (WHERE NULLIF(BTRIM(stock_marked_to_bank), '') IS NOT NULL) AS legacy_named,
 COUNT(stock_marked_bank_id) AS assigned_ids,
 COUNT(*) FILTER (WHERE NULLIF(BTRIM(stock_marked_to_bank), '') IS NOT NULL AND stock_marked_bank_id IS NULL) AS unresolved
FROM contract_details;
-- Unmatched/ambiguous names: manually reconcile before enabling the ID-only report.
SELECT cd.contract_detail_id, cd.contract_id, cd.stock_marked_to_bank,
 COUNT(b.id) AS matching_banks, ARRAY_AGG(b.id) FILTER (WHERE b.id IS NOT NULL) AS candidate_ids
FROM contract_details cd LEFT JOIN bank_masters b
 ON LOWER(BTRIM(cd.stock_marked_to_bank)) = LOWER(BTRIM(b.bank_name))
WHERE NULLIF(BTRIM(cd.stock_marked_to_bank), '') IS NOT NULL AND cd.stock_marked_bank_id IS NULL
GROUP BY cd.contract_detail_id, cd.contract_id, cd.stock_marked_to_bank ORDER BY cd.contract_detail_id;
-- Orphans must be zero.
SELECT cd.* FROM contract_details cd LEFT JOIN bank_masters b ON b.id = cd.stock_marked_bank_id
WHERE cd.stock_marked_bank_id IS NOT NULL AND b.id IS NULL;
-- Backup coverage and unchanged legacy values must both be zero (during maintenance).
SELECT COUNT(*) AS missing_backup FROM contract_details cd LEFT JOIN stock_bank_mapping_backup old
 USING (contract_detail_id) WHERE old.contract_detail_id IS NULL;
SELECT COUNT(*) AS changed_legacy_values FROM contract_details cd JOIN stock_bank_mapping_backup old
 USING (contract_detail_id) WHERE cd.stock_marked_to_bank IS DISTINCT FROM old.stock_marked_to_bank;
SELECT cd.contract_detail_id, cd.contract_id, old.stock_marked_to_bank AS original_name,
 old.original_bank_id, cd.stock_marked_bank_id, b.bank_name AS current_master_name
FROM contract_details cd JOIN stock_bank_mapping_backup old USING (contract_detail_id)
LEFT JOIN bank_masters b ON b.id = cd.stock_marked_bank_id ORDER BY cd.contract_detail_id;
-- Reconcile duplicate detail rows: application edits the first detail row.
SELECT contract_id, COUNT(*) AS detail_rows FROM contract_details GROUP BY contract_id HAVING COUNT(*) > 1;

-- Immediately after migration, unique matches with a different ID require review.
WITH unique_bank AS (
 SELECT LOWER(BTRIM(bank_name)) AS name, MIN(id) AS id FROM bank_masters
 GROUP BY LOWER(BTRIM(bank_name)) HAVING COUNT(*) = 1
)
SELECT cd.contract_detail_id, cd.stock_marked_to_bank, cd.stock_marked_bank_id, b.id AS expected_id
FROM contract_details cd JOIN unique_bank b ON LOWER(BTRIM(cd.stock_marked_to_bank)) = b.name
WHERE cd.stock_marked_bank_id IS DISTINCT FROM b.id;

-- Explicitly approved alias must map to ID 1; original generic text is retained.
SELECT contract_detail_id, contract_id, stock_marked_to_bank, stock_marked_bank_id
FROM contract_details WHERE LOWER(BTRIM(stock_marked_to_bank)) = 'union bank of india'
 AND stock_marked_bank_id IS DISTINCT FROM 1;
-- Pre-existing non-null IDs must not change.
SELECT cd.contract_detail_id, old.original_bank_id, cd.stock_marked_bank_id
FROM contract_details cd JOIN stock_bank_mapping_backup old USING (contract_detail_id)
WHERE old.original_bank_id IS NOT NULL AND cd.stock_marked_bank_id IS DISTINCT FROM old.original_bank_id;
