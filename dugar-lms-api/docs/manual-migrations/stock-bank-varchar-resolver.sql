SELECT cd.contract_detail_id,cd.stock_marked_to_bank AS original_value,
 NULLIF(BTRIM(cd.stock_marked_to_bank),'') AS raw_value,
 to_jsonb(cd)->>'stock_marked_bank_id' AS source_id,
 CASE
  WHEN NULLIF(BTRIM(cd.stock_marked_to_bank),'') IS NULL THEN
   (SELECT id FROM bank_masters WHERE id::text=to_jsonb(cd)->>'stock_marked_bank_id')
  WHEN LOWER(BTRIM(cd.stock_marked_to_bank)) IN ('union bank of india','union bank of india - cash credit') THEN
   (SELECT id FROM bank_masters WHERE id=1)
  WHEN BTRIM(cd.stock_marked_to_bank) ~ '^[0-9]+$' THEN
   (SELECT id FROM bank_masters WHERE id::text=COALESCE(NULLIF(LTRIM(BTRIM(cd.stock_marked_to_bank),'0'),''),'0'))
  ELSE (SELECT MIN(id) FROM bank_masters WHERE LOWER(BTRIM(bank_name))=LOWER(BTRIM(cd.stock_marked_to_bank)) HAVING COUNT(*)=1)
 END AS target_id
FROM contract_details cd;
