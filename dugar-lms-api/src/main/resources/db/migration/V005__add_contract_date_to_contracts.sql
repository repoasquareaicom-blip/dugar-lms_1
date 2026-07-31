-- Store legacy CONT_DT from the contract Excel migration.

ALTER TABLE contracts
ADD COLUMN IF NOT EXISTS contract_date DATE;
