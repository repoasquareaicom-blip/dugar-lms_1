ALTER TABLE party_masters
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS partner_1_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS partner_2_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS residence_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS distance_km NUMERIC(10,2);

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS co_applicant_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS guarantor_2_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS is_draft BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_party_masters_party_type'
          AND conrelid = 'party_masters'::regclass
    ) THEN
        ALTER TABLE party_masters
            DROP CONSTRAINT chk_party_masters_party_type;
    END IF;
END $$;

ALTER TABLE party_masters
    ADD CONSTRAINT chk_party_masters_party_type CHECK (
        party_type IN ('BORROWER', 'CO_APPLICANT', 'GUARANTOR')
    );

CREATE INDEX IF NOT EXISTS idx_contracts_co_applicant_code
    ON contracts (co_applicant_code);

CREATE INDEX IF NOT EXISTS idx_contracts_guarantor_2_code
    ON contracts (guarantor_2_code);
