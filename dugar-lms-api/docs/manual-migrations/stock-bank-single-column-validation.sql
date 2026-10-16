-- Read-only checks for the converted schema, before or after source retirement.
SELECT column_name,data_type FROM information_schema.columns WHERE table_schema='public'
 AND table_name='contract_details' AND column_name IN ('stock_marked_to_bank','stock_marked_bank_id');
SELECT COUNT(*) AS current_rows,(SELECT COUNT(*) FROM stock_bank_contract_details_backup_v2) AS backup_rows,
 COUNT(stock_marked_to_bank) AS assigned FROM contract_details;
SELECT cd.contract_detail_id,cd.stock_marked_to_bank FROM contract_details cd
 LEFT JOIN bank_masters b ON b.id=cd.stock_marked_to_bank
 WHERE cd.stock_marked_to_bank IS NOT NULL AND b.id IS NULL;
-- Pre-existing valid source IDs must remain unchanged.
SELECT cd.contract_detail_id,old.stock_marked_bank_id,cd.stock_marked_to_bank
FROM contract_details cd JOIN stock_bank_contract_details_backup_v2 old USING(contract_detail_id)
WHERE old.stock_marked_bank_id IS NOT NULL AND cd.stock_marked_to_bank IS DISTINCT FROM old.stock_marked_bank_id;
-- Named assignments from the complete pre-conversion backup must still be assigned.
SELECT cd.contract_detail_id,old.stock_marked_to_bank FROM contract_details cd
 JOIN stock_bank_contract_details_backup_v2 old USING(contract_detail_id)
WHERE NULLIF(BTRIM(old.stock_marked_to_bank),'') IS NOT NULL AND cd.stock_marked_to_bank IS NULL;
-- All 51 originally migrated Union Bank contracts must retain ID 1.
SELECT COUNT(*) AS original_union_contracts,
 COUNT(*) FILTER(WHERE cd.stock_marked_to_bank=1) AS still_bank_1
FROM stock_bank_mapping_backup original JOIN contract_details cd USING(contract_detail_id)
WHERE NULLIF(BTRIM(original.stock_marked_to_bank),'') IS NOT NULL;
SELECT contract_id,COUNT(*) FROM contract_details GROUP BY contract_id HAVING COUNT(*)>1;
SELECT conname,convalidated,pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid='contract_details'::regclass AND conname='fk_contract_details_stock_marked_to_bank';
SELECT stock_marked_to_bank,COUNT(*) FROM contract_details
WHERE stock_marked_to_bank IS NOT NULL GROUP BY stock_marked_to_bank ORDER BY stock_marked_to_bank;
