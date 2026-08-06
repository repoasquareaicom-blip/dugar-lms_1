CREATE TABLE IF NOT EXISTS ledger_opening_balances (
    opening_balance_id BIGSERIAL PRIMARY KEY,
    ledger_id BIGINT NOT NULL,
    ledger_code VARCHAR(100) NOT NULL,
    account_balance NUMERIC(18, 2) NOT NULL DEFAULT 0,
    entered_date DATE NOT NULL DEFAULT CURRENT_DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_opening_balances_ledger
        FOREIGN KEY (ledger_id)
        REFERENCES ledger_codes (ledger_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ledger_opening_balances_code
    ON ledger_opening_balances (ledger_code);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_opening_balances_active_code
    ON ledger_opening_balances (UPPER(ledger_code))
    WHERE is_active = TRUE;
