-- Remaining Financial Terms fields live on contracts or contract_details.

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS repayment_terms VARCHAR(100),
    ADD COLUMN IF NOT EXISTS moratorium_months INTEGER,
    ADD COLUMN IF NOT EXISTS payment_frequency VARCHAR(100),
    ADD COLUMN IF NOT EXISTS repayment_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS enach_applicable BOOLEAN;

ALTER TABLE contract_details
    ADD COLUMN IF NOT EXISTS stamp_duty NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS rto_charges NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS valuation_charges NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS rc_holding_amount NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS other_charges NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS payment_done_to VARCHAR(100),
    ADD COLUMN IF NOT EXISTS payee_1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS payee_2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS payee_3 VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_contract_details_contract_id'
          AND conrelid = 'contract_details'::regclass
    ) THEN
        ALTER TABLE contract_details
            ADD CONSTRAINT uq_contract_details_contract_id UNIQUE (contract_id);
    END IF;
END $$;

ALTER TABLE contracts
    ADD CONSTRAINT chk_contracts_moratorium_months CHECK (
        moratorium_months IS NULL OR moratorium_months >= 0
    );
