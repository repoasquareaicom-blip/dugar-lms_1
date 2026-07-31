-- Voucher migration schema. Vouchers are one-to-many and stay outside contracts/contract_details.

CREATE TABLE IF NOT EXISTS voucher_headers (
    voucher_header_id BIGSERIAL PRIMARY KEY,
    voucher_type VARCHAR(20) NOT NULL,
    voucher_type_description VARCHAR(100),
    voucher_number VARCHAR(100) NOT NULL,
    voucher_date DATE,
    system_date DATE,
    transaction_type VARCHAR(100),
    voucher_amount NUMERIC(18,2),
    receipt_number VARCHAR(100),
    temporary_receipt_number VARCHAR(100),
    temporary_receipt_date DATE,
    contract_number VARCHAR(100),
    contract_type VARCHAR(50),
    contract_id BIGINT,
    bank_code VARCHAR(100),
    header_control_code VARCHAR(100),
    header_control_name VARCHAR(255),
    remarks TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT uq_voucher_headers_type_number UNIQUE (voucher_type, voucher_number),
    CONSTRAINT fk_voucher_headers_contract_id
        FOREIGN KEY (contract_id)
        REFERENCES contracts (contract_id)
        ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS voucher_details (
    voucher_detail_id BIGSERIAL PRIMARY KEY,
    voucher_header_id BIGINT NOT NULL,
    serial_number INTEGER NOT NULL,
    category VARCHAR(50),
    ledger_code VARCHAR(100),
    ledger_name VARCHAR(255),
    sub_ledger_code VARCHAR(100),
    debit_amount NUMERIC(18,2),
    credit_amount NUMERIC(18,2),
    party_code VARCHAR(100),
    party_name VARCHAR(255),
    loan_reference VARCHAR(100),
    narration TEXT,
    address TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT uq_voucher_details_header_serial UNIQUE (voucher_header_id, serial_number),
    CONSTRAINT fk_voucher_details_header_id
        FOREIGN KEY (voucher_header_id)
        REFERENCES voucher_headers (voucher_header_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_voucher_headers_voucher_number
    ON voucher_headers (voucher_number);

CREATE INDEX IF NOT EXISTS idx_voucher_headers_voucher_date
    ON voucher_headers (voucher_date);

CREATE INDEX IF NOT EXISTS idx_voucher_headers_contract_id
    ON voucher_headers (contract_id);

CREATE INDEX IF NOT EXISTS idx_voucher_details_header_id
    ON voucher_details (voucher_header_id);

CREATE INDEX IF NOT EXISTS idx_voucher_details_ledger_code
    ON voucher_details (ledger_code);
