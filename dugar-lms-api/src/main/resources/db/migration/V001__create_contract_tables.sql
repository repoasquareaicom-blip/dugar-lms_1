-- V001__create_contract_tables.sql
-- Contract module schema for PostgreSQL

CREATE TABLE IF NOT EXISTS contracts (
    contract_id BIGSERIAL PRIMARY KEY,

    -- Standard audit columns
    id BIGSERIAL NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP,
    updated_by VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    contract_type VARCHAR(50) NOT NULL,
    contract_number VARCHAR(100) NOT NULL,
    legacy_contract_number VARCHAR(100),
    contract_date DATE,
    loan_amount NUMERIC(18,2),
    first_emi_date DATE,
    tenure_months INTEGER,
    flat_interest_rate NUMERIC(7,4),
    insurance_deposit NUMERIC(18,2),
    finance_charges NUMERIC(18,2),
    bpfc_days INTEGER,
    bpfc_rate NUMERIC(7,4),
    bpfc_amount NUMERIC(18,2),
    total_contract_value NUMERIC(18,2),
    prompt_payment_rebate NUMERIC(18,2),
    irr_rate NUMERIC(7,4),
    borrower_code VARCHAR(50),
    guarantor_code VARCHAR(50),
    area_code VARCHAR(50),
    registration_number VARCHAR(50),
    vehicle_make VARCHAR(100),
    equipment_model VARCHAR(100),
    loan_close_date DATE,
    category VARCHAR(50),
    pdc_last_date DATE,
    mode_of_payment VARCHAR(50),
    vehicle_age INTEGER,
    status VARCHAR(30) NOT NULL,

    CONSTRAINT uq_contracts_id UNIQUE (id),
    CONSTRAINT uq_contracts_type_number UNIQUE (contract_type, contract_number),
    CONSTRAINT chk_contracts_vehicle_age CHECK (
        vehicle_age IS NULL OR vehicle_age >= 0
    ),
    CONSTRAINT chk_contracts_tenure_months CHECK (
        tenure_months IS NULL OR tenure_months > 0
    ),
    CONSTRAINT chk_contracts_bpfc_days CHECK (
        bpfc_days IS NULL OR bpfc_days >= 0
    )
);

CREATE TABLE IF NOT EXISTS contract_details (
    contract_detail_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,

    -- Standard audit columns
    id BIGSERIAL NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP,
    updated_by VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    existing_loan_details TEXT,
    linked_account VARCHAR(100),
    processing_charges NUMERIC(18,2),
    emi_advance NUMERIC(18,2),
    state_code VARCHAR(20),
    additional_collateral TEXT,
    engine_number VARCHAR(100),
    chassis_number VARCHAR(100),
    registration_date DATE,
    loan_approved_by VARCHAR(100),
    borrower_fi_by VARCHAR(100),
    vehicle_inspection_by VARCHAR(100),
    documents_obtained_by VARCHAR(100),
    loan_referred_by VARCHAR(100),
    owner_serial_number VARCHAR(100),

    CONSTRAINT uq_contract_details_id UNIQUE (id),
    CONSTRAINT fk_contract_details_contract_id
        FOREIGN KEY (contract_id)
        REFERENCES contracts (contract_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_contracts_contract_number
    ON contracts (contract_number);

CREATE INDEX IF NOT EXISTS idx_contracts_borrower_code
    ON contracts (borrower_code);

CREATE INDEX IF NOT EXISTS idx_contracts_registration_number
    ON contracts (registration_number);

CREATE INDEX IF NOT EXISTS idx_contracts_status
    ON contracts (status);

CREATE INDEX IF NOT EXISTS idx_contract_details_engine_number
    ON contract_details (engine_number);

CREATE INDEX IF NOT EXISTS idx_contract_details_chassis_number
    ON contract_details (chassis_number);
