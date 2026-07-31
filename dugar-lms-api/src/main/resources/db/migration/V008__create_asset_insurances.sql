-- Insurance policy details for migrated assets.

CREATE TABLE IF NOT EXISTS asset_insurances (
    asset_insurance_id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    insurance_company_code VARCHAR(50),
    policy_number VARCHAR(150),
    cover_note_number VARCHAR(150),
    policy_date DATE,
    valid_from DATE,
    valid_to DATE,
    policy_by VARCHAR(150),
    premium_amount NUMERIC(18,2),
    source_row_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_asset_insurances_asset_id
        FOREIGN KEY (asset_id)
        REFERENCES assets (asset_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_asset_insurances_asset_id
    ON asset_insurances (asset_id);

CREATE INDEX IF NOT EXISTS idx_asset_insurances_policy_number
    ON asset_insurances (policy_number);

CREATE INDEX IF NOT EXISTS idx_asset_insurances_company_code
    ON asset_insurances (insurance_company_code);

CREATE INDEX IF NOT EXISTS idx_asset_insurances_valid_to
    ON asset_insurances (valid_to);
