-- READ ONLY. Works before stock_marked_bank_id exists.
WITH matches AS (
 SELECT cd.contract_detail_id, COUNT(b.id) AS bank_matches
 FROM contract_details cd LEFT JOIN bank_masters b
 ON LOWER(BTRIM(cd.stock_marked_to_bank)) = LOWER(BTRIM(b.bank_name))
 WHERE NULLIF(BTRIM(cd.stock_marked_to_bank), '') IS NOT NULL GROUP BY cd.contract_detail_id
)
SELECT COUNT(*) AS legacy_named,
 COUNT(*) FILTER (WHERE bank_matches = 1) AS unique_matches,
 COUNT(*) FILTER (WHERE bank_matches = 0) AS unmatched,
 COUNT(*) FILTER (WHERE bank_matches > 1) AS ambiguous FROM matches;
SELECT cd.contract_detail_id, cd.contract_id, cd.stock_marked_to_bank, COUNT(b.id) AS bank_matches
FROM contract_details cd LEFT JOIN bank_masters b
 ON LOWER(BTRIM(cd.stock_marked_to_bank)) = LOWER(BTRIM(b.bank_name))
WHERE NULLIF(BTRIM(cd.stock_marked_to_bank), '') IS NOT NULL
GROUP BY cd.contract_detail_id, cd.contract_id, cd.stock_marked_to_bank HAVING COUNT(b.id) <> 1;
SELECT contract_id, COUNT(*) AS detail_rows FROM contract_details GROUP BY contract_id HAVING COUNT(*) > 1;
