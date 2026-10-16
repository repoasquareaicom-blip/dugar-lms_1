-- MANUAL ONLY: confirmed local development; no datatype conversion.
-- Full external backup and application/dependency checks required before execution.
BEGIN;
SET LOCAL lock_timeout='5s';
LOCK TABLE contract_details IN ACCESS EXCLUSIVE MODE NOWAIT;
LOCK TABLE bank_masters IN SHARE MODE NOWAIT;
DO $$ BEGIN
 IF current_database()<>'dugar_lms' OR inet_server_addr()::text NOT IN ('127.0.0.1/32','::1/128') THEN
  RAISE EXCEPTION 'Not the confirmed loopback development database';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
  AND table_name='contract_details' AND column_name='stock_marked_to_bank' AND data_type='character varying') THEN
  RAISE EXCEPTION 'stock_marked_to_bank must remain VARCHAR';
 END IF;
END $$;
CREATE TEMP TABLE stock_bank_varchar_resolution ON COMMIT DROP AS
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
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM stock_bank_varchar_resolution
            WHERE (raw_value IS NOT NULL OR source_id IS NOT NULL) AND target_id IS NULL) THEN
  RAISE EXCEPTION 'Unmatched or ambiguous bank values: migration stopped';
 END IF;
 IF EXISTS (SELECT 1 FROM stock_bank_varchar_resolution
            WHERE source_id IS NOT NULL AND source_id IS DISTINCT FROM target_id::text) THEN
  RAISE EXCEPTION 'Existing source bank ID conflicts with legacy value: migration stopped';
 END IF;
END $$;
-- Preserve every original field, including the extra column, before changing any value.
CREATE TABLE IF NOT EXISTS stock_bank_varchar_backup_final AS TABLE contract_details;
UPDATE contract_details cd SET stock_marked_to_bank=r.target_id::text
FROM stock_bank_varchar_resolution r WHERE cd.contract_detail_id=r.contract_detail_id
 AND cd.stock_marked_to_bank IS DISTINCT FROM r.target_id::text;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM contract_details cd LEFT JOIN bank_masters b ON b.id::text=cd.stock_marked_to_bank
            WHERE cd.stock_marked_to_bank IS NOT NULL AND b.id IS NULL)
 OR EXISTS (SELECT 1 FROM stock_bank_varchar_resolution r JOIN contract_details cd USING(contract_detail_id)
            WHERE cd.stock_marked_to_bank IS DISTINCT FROM r.target_id::text) THEN
  RAISE EXCEPTION 'Reconciliation failed: rollback';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='contract_details'::regclass
  AND conname='ck_contract_details_stock_bank_numeric_string') THEN
  ALTER TABLE contract_details ADD CONSTRAINT ck_contract_details_stock_bank_numeric_string
   CHECK (stock_marked_to_bank IS NULL OR stock_marked_to_bank ~ '^[1-9][0-9]*$') NOT VALID;
 END IF;
END $$;
ALTER TABLE contract_details VALIDATE CONSTRAINT ck_contract_details_stock_bank_numeric_string;
CREATE INDEX IF NOT EXISTS idx_contract_details_stock_bank_varchar ON contract_details(stock_marked_to_bank);
COMMIT;
-- Run validation + updated application checks before stock-bank-varchar-retire-source.sql.
