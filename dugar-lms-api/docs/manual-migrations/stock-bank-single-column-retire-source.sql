-- MANUAL ONLY: run AFTER converted-schema application checks and source-reference audit pass.
-- No CASCADE: dependent views/functions must be reviewed rather than silently removed.
BEGIN;
SET LOCAL lock_timeout='10s';
LOCK TABLE contract_details IN ACCESS EXCLUSIVE MODE;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
                AND table_name='contract_details' AND column_name='stock_marked_to_bank' AND data_type='bigint')
    OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='contract_details'::regclass
                   AND conname='fk_contract_details_stock_marked_to_bank' AND convalidated)
    OR to_regclass('public.stock_bank_contract_details_backup_v2') IS NULL THEN
  RAISE EXCEPTION 'Converted column, full backup and validated FK are required';
 END IF;
 IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
            AND table_name='contract_details' AND column_name='stock_marked_bank_id') THEN
  IF EXISTS (SELECT 1 FROM contract_details WHERE stock_marked_to_bank IS DISTINCT FROM stock_marked_bank_id) THEN
   RAISE EXCEPTION 'Source and destination differ: resolve before retiring source';
  END IF;
  ALTER TABLE contract_details DROP COLUMN stock_marked_bank_id;
 END IF;
END $$;
COMMIT;
