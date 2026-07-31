-- Party master data for migrated borrowers and guarantors.

CREATE TABLE IF NOT EXISTS party_masters (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP,
    updated_by VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    party_code VARCHAR(50) NOT NULL,
    party_type VARCHAR(20) NOT NULL,
    salutation VARCHAR(50),
    full_name VARCHAR(255) NOT NULL,
    swd_name VARCHAR(255),
    address_line_1 TEXT,
    address_line_2 TEXT,
    area VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    pin_code VARCHAR(20),
    contact_number VARCHAR(50),
    alternative_number VARCHAR(50),
    email_id VARCHAR(255),
    date_of_birth DATE,
    pan_number VARCHAR(20),
    aadhaar_number VARCHAR(20),
    occupation VARCHAR(100),
    annual_income NUMERIC(18,2),
    firm_name VARCHAR(255),

    CONSTRAINT uq_party_masters_party_code UNIQUE (party_code),
    CONSTRAINT chk_party_masters_party_type CHECK (party_type IN ('BORROWER', 'GUARANTOR'))
);

CREATE INDEX IF NOT EXISTS idx_party_masters_party_code
    ON party_masters (party_code);

CREATE INDEX IF NOT EXISTS idx_party_masters_party_type
    ON party_masters (party_type);
