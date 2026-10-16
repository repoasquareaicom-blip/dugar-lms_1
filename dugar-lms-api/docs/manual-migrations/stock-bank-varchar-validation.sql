-- Read-only validation, usable before or after source retirement.
SELECT column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public'
 AND table_name='contract_details' AND column_name IN ('stock_marked_to_bank','stock_marked_bank_id') ORDER BY column_name;
SELECT COUNT(*) AS total_rows,COUNT(stock_marked_to_bank) AS assignments FROM contract_details;
SELECT cd.contract_detail_id,cd.stock_marked_to_bank FROM contract_details cd
 LEFT JOIN bank_masters b ON b.id::text=cd.stock_marked_to_bank
 WHERE cd.stock_marked_to_bank IS NOT NULL AND b.id IS NULL;
SELECT cd.contract_detail_id,old.stock_marked_to_bank FROM contract_details cd
 JOIN stock_bank_varchar_backup_final old USING(contract_detail_id)
 WHERE NULLIF(BTRIM(old.stock_marked_to_bank),'') IS NOT NULL AND cd.stock_marked_to_bank IS NULL;
SELECT cd.contract_detail_id FROM contract_details cd JOIN stock_bank_varchar_backup_final old USING(contract_detail_id)
 WHERE to_jsonb(old)->>'stock_marked_bank_id' IS NOT NULL
 AND cd.stock_marked_to_bank IS DISTINCT FROM (to_jsonb(old)->>'stock_marked_bank_id');
SELECT COUNT(*) AS original_union,COUNT(*) FILTER(WHERE cd.stock_marked_to_bank='1') AS still_bank_1
FROM stock_bank_mapping_backup old JOIN contract_details cd USING(contract_detail_id)
WHERE NULLIF(BTRIM(old.stock_marked_to_bank),'') IS NOT NULL;
SELECT contract_id,COUNT(*) FROM contract_details GROUP BY contract_id HAVING COUNT(*)>1;
SELECT stock_marked_to_bank,b.bank_name,COUNT(*) FROM contract_details cd
 JOIN bank_masters b ON b.id::text=cd.stock_marked_to_bank GROUP BY stock_marked_to_bank,b.bank_name ORDER BY stock_marked_to_bank;
SELECT conname,convalidated FROM pg_constraint WHERE conrelid='contract_details'::regclass
 AND conname='ck_contract_details_stock_bank_numeric_string';
