-- Asset details migrated from Oracle LMS contract asset sources.

CREATE TABLE IF NOT EXISTS assets (
    asset_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    oracle_contract_type VARCHAR(50),
    oracle_contract_no VARCHAR(100),
    finance_type VARCHAR(50),
    vehicle_type_code VARCHAR(50),
    registration_number VARCHAR(50),
    engine_number VARCHAR(100),
    chassis_number VARCHAR(100),
    manufacture_year VARCHAR(20),
    equipment_value NUMERIC(18,2),
    security_offered TEXT,
    owner_serial_no VARCHAR(100),
    source_table VARCHAR(100),
    source_row_hash VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_assets_contract_id
        FOREIGN KEY (contract_id)
        REFERENCES contracts (contract_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assets_contract_id
    ON assets (contract_id);

CREATE INDEX IF NOT EXISTS idx_assets_oracle_contract
    ON assets (oracle_contract_type, oracle_contract_no);

CREATE INDEX IF NOT EXISTS idx_assets_registration_number
    ON assets (registration_number);

CREATE INDEX IF NOT EXISTS idx_assets_engine_number
    ON assets (engine_number);

CREATE INDEX IF NOT EXISTS idx_assets_chassis_number
    ON assets (chassis_number);
