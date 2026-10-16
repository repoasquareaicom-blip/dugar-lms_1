-- MANUAL ONLY. Confirm local/development target and take mapping backup first.
-- Approved resolution: generic UNION BANK OF INDIA -> ID 1 (Cash Credit).
-- Bank ID 10 (Term Loan) remains separate. Never overwrite existing IDs or legacy text.
BEGIN;
SET LOCAL lock_timeout = '10s';
LOCK TABLE contract_details IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE bank_masters IN SHARE MODE;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM bank_masters WHERE id = 1
                AND LOWER(BTRIM(bank_name)) = 'union bank of india - cash credit') THEN
  RAISE EXCEPTION 'Approved Bank ID 1 Cash Credit mapping is not available';
 END IF;
 IF (SELECT COUNT(*) FROM bank_masters WHERE LOWER(BTRIM(bank_name)) = 'union bank of india') > 1
    OR EXISTS (SELECT 1 FROM bank_masters WHERE LOWER(BTRIM(bank_name)) = 'union bank of india' AND id <> 1) THEN
  RAISE EXCEPTION 'Generic Union Bank master conflicts with approved mapping';
 END IF;
END $$;
CREATE TEMP TABLE stock_bank_migration_name_map ON COMMIT DROP AS
 SELECT LOWER(BTRIM(bank_name)) AS name, MIN(id) AS bank_id
 FROM bank_masters GROUP BY LOWER(BTRIM(bank_name)) HAVING COUNT(*) = 1;
INSERT INTO stock_bank_migration_name_map (name, bank_id)
 SELECT 'union bank of india', 1 WHERE NOT EXISTS (
  SELECT 1 FROM stock_bank_migration_name_map WHERE name = 'union bank of india');
DO $$ BEGIN
 IF EXISTS (
  SELECT 1 FROM contract_details cd
  WHERE NULLIF(BTRIM(cd.stock_marked_to_bank), '') IS NOT NULL
    AND (SELECT COUNT(*) FROM stock_bank_migration_name_map m
         WHERE m.name = LOWER(BTRIM(cd.stock_marked_to_bank))) <> 1
 ) THEN
  RAISE EXCEPTION 'Unmatched or ambiguous bank names: migration stopped';
 END IF;
 IF EXISTS (SELECT 1 FROM contract_details GROUP BY contract_id HAVING COUNT(*) > 1) THEN
  RAISE EXCEPTION 'Duplicate contract detail rows: migration stopped';
 END IF;
END $$;
CREATE TABLE IF NOT EXISTS stock_bank_mapping_backup (
 contract_detail_id BIGINT PRIMARY KEY, contract_id BIGINT NOT NULL,
 stock_marked_to_bank TEXT, original_bank_id BIGINT,
 backed_up_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE contract_details ADD COLUMN IF NOT EXISTS stock_marked_bank_id BIGINT;
INSERT INTO stock_bank_mapping_backup (contract_detail_id, contract_id, stock_marked_to_bank, original_bank_id)
SELECT contract_detail_id, contract_id, stock_marked_to_bank, stock_marked_bank_id
FROM contract_details ON CONFLICT (contract_detail_id) DO NOTHING;
UPDATE contract_details cd SET stock_marked_bank_id = m.bank_id
FROM stock_bank_migration_name_map m
WHERE cd.stock_marked_bank_id IS NULL AND NULLIF(BTRIM(cd.stock_marked_to_bank), '') IS NOT NULL
 AND LOWER(BTRIM(cd.stock_marked_to_bank)) = m.name;
CREATE INDEX IF NOT EXISTS idx_contract_details_stock_bank_id ON contract_details(stock_marked_bank_id);
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'contract_details'::regclass
                AND conname = 'fk_contract_details_stock_bank') THEN
  ALTER TABLE contract_details ADD CONSTRAINT fk_contract_details_stock_bank
   FOREIGN KEY (stock_marked_bank_id) REFERENCES bank_masters(id) NOT VALID;
 END IF;
END $$;
COMMIT;
-- After reconciliation, separately validate fk_contract_details_stock_bank.
