-- MANUAL ONLY. Run after numeric-string migration, reconciliation and dependency audit.
BEGIN;
SET LOCAL lock_timeout='5s';
LOCK TABLE contract_details IN ACCESS EXCLUSIVE MODE NOWAIT;
LOCK TABLE bank_masters IN SHARE MODE NOWAIT;
DO $$ BEGIN
 IF current_database()<>'dugar_lms' OR inet_server_addr()::text NOT IN ('127.0.0.1/32','::1/128')
 OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='contract_details'
                AND column_name='stock_marked_to_bank' AND data_type='character varying')
 OR to_regclass('public.stock_bank_varchar_backup_final') IS NULL THEN
  RAISE EXCEPTION 'Development target, VARCHAR column and full backup required';
 END IF;
 IF EXISTS (SELECT 1 FROM contract_details cd LEFT JOIN bank_masters b ON b.id::text=cd.stock_marked_to_bank
            WHERE NULLIF(BTRIM(cd.stock_marked_to_bank),'') IS NOT NULL AND b.id IS NULL) THEN
  RAISE EXCEPTION 'Unmatched bank IDs remain';
 END IF;
 IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='contract_details'
            AND column_name='stock_marked_bank_id') THEN
  IF EXISTS (SELECT 1 FROM contract_details WHERE stock_marked_bank_id IS NOT NULL
             AND stock_marked_to_bank IS DISTINCT FROM stock_marked_bank_id::text) THEN
   RAISE EXCEPTION 'Existing source IDs would be lost';
  END IF;
  ALTER TABLE contract_details DROP COLUMN stock_marked_bank_id; -- No CASCADE.
 END IF;
END $$;
COMMIT;
