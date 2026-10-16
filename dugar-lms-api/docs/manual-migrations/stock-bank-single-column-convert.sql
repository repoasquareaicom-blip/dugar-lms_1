-- MANUAL LOCAL/DEVELOPMENT CUTOVER ONLY; stop writers and back up the database first.
-- Run with updated application ready. Keeps the source column for verification.
BEGIN;
SET LOCAL lock_timeout = '10s';
LOCK TABLE contract_details IN ACCESS EXCLUSIVE MODE;
LOCK TABLE bank_masters IN SHARE MODE;
DO $$
DECLARE old_type TEXT;
BEGIN
 SELECT data_type INTO old_type FROM information_schema.columns
 WHERE table_schema='public' AND table_name='contract_details' AND column_name='stock_marked_to_bank';
 IF old_type = 'bigint' THEN
  IF EXISTS (SELECT 1 FROM contract_details cd LEFT JOIN bank_masters b ON b.id=cd.stock_marked_to_bank
             WHERE cd.stock_marked_to_bank IS NOT NULL AND b.id IS NULL) THEN
   RAISE EXCEPTION 'Existing converted column has orphan IDs';
  END IF;
  RETURN; -- Never copy a stale source over new application edits on a rerun.
 END IF;
 IF old_type IS DISTINCT FROM 'character varying' THEN
  RAISE EXCEPTION 'Unexpected bank-column type: %',old_type;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
                AND table_name='contract_details' AND column_name='stock_marked_bank_id' AND data_type='bigint') THEN
  RAISE EXCEPTION 'Validated source column stock_marked_bank_id is required';
 END IF;
 IF to_regclass('public.stock_bank_contract_details_backup_v2') IS NOT NULL THEN
  RAISE EXCEPTION 'Full backup table already exists: review it before legacy conversion';
 END IF;
 -- Complete snapshot of every field, including both bank columns, before any updates.
 CREATE TABLE stock_bank_contract_details_backup_v2 AS TABLE contract_details;
 IF EXISTS (SELECT 1 FROM contract_details cd LEFT JOIN bank_masters b ON b.id=cd.stock_marked_bank_id
            WHERE cd.stock_marked_bank_id IS NOT NULL AND b.id IS NULL) THEN
  RAISE EXCEPTION 'Source column contains invalid bank IDs';
 END IF;
 -- Resolve missing source IDs only where the name identifies exactly one master bank.
 -- Existing non-null IDs are never replaced. Remaining names fail validation below.
 WITH unique_bank AS (
  SELECT LOWER(BTRIM(bank_name)) AS name,MIN(id) AS bank_id FROM bank_masters
  GROUP BY LOWER(BTRIM(bank_name)) HAVING COUNT(*)=1
 )
 UPDATE contract_details cd SET stock_marked_bank_id=b.bank_id FROM unique_bank b
 WHERE cd.stock_marked_bank_id IS NULL AND LOWER(BTRIM(cd.stock_marked_to_bank))=b.name;
 -- Approved historical alias remains valid only for Cash Credit ID 1.
 IF EXISTS (SELECT 1 FROM contract_details WHERE LOWER(BTRIM(stock_marked_to_bank))='union bank of india')
    AND NOT EXISTS (SELECT 1 FROM bank_masters WHERE id=1 AND LOWER(BTRIM(bank_name))='union bank of india - cash credit') THEN
  RAISE EXCEPTION 'Approved historical alias no longer identifies Cash Credit ID 1';
 END IF;
 UPDATE contract_details SET stock_marked_bank_id=1
 WHERE stock_marked_bank_id IS NULL AND LOWER(BTRIM(stock_marked_to_bank))='union bank of india';
 IF EXISTS (
  SELECT 1 FROM contract_details cd LEFT JOIN bank_masters b ON b.id=cd.stock_marked_bank_id
  WHERE NULLIF(BTRIM(cd.stock_marked_to_bank),'') IS NOT NULL AND (
   cd.stock_marked_bank_id IS NULL
   OR NOT (LOWER(BTRIM(cd.stock_marked_to_bank))=LOWER(BTRIM(b.bank_name))
           OR LOWER(BTRIM(cd.stock_marked_to_bank))='union bank of india' AND b.id=1)
   OR (SELECT COUNT(*) FROM bank_masters named WHERE LOWER(BTRIM(named.bank_name))=LOWER(BTRIM(cd.stock_marked_to_bank)))>1
  )
 ) THEN RAISE EXCEPTION 'Unmatched, ambiguous or conflicting legacy bank names: rollback'; END IF;
 ALTER TABLE contract_details ALTER COLUMN stock_marked_to_bank TYPE BIGINT USING stock_marked_bank_id;
 IF (SELECT COUNT(*) FROM contract_details) <> (SELECT COUNT(*) FROM stock_bank_contract_details_backup_v2)
    OR EXISTS (
     SELECT 1 FROM contract_details cd JOIN stock_bank_contract_details_backup_v2 old USING(contract_detail_id)
     WHERE cd.stock_marked_to_bank IS DISTINCT FROM cd.stock_marked_bank_id
        OR old.stock_marked_bank_id IS NOT NULL AND cd.stock_marked_to_bank IS DISTINCT FROM old.stock_marked_bank_id
        OR (to_jsonb(cd)-'stock_marked_to_bank'-'stock_marked_bank_id')
           IS DISTINCT FROM (to_jsonb(old)-'stock_marked_to_bank'-'stock_marked_bank_id')
    ) THEN RAISE EXCEPTION 'Counts, preserved IDs, or unrelated fields failed reconciliation'; END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_contract_details_stock_marked_to_bank ON contract_details(stock_marked_to_bank);
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='contract_details'::regclass
                AND conname='fk_contract_details_stock_marked_to_bank') THEN
  ALTER TABLE contract_details ADD CONSTRAINT fk_contract_details_stock_marked_to_bank
   FOREIGN KEY(stock_marked_to_bank) REFERENCES bank_masters(id) NOT VALID;
 END IF;
END $$;
ALTER TABLE contract_details VALIDATE CONSTRAINT fk_contract_details_stock_marked_to_bank;
COMMIT;
-- Run application acceptance checks and stock-bank-single-column-validation.sql.
-- Only then run stock-bank-single-column-retire-source.sql; retain the backup table.
