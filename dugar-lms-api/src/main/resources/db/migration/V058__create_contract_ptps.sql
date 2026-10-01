CREATE TABLE IF NOT EXISTS contract_ptps (
    contract_ptp_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    ptp_date DATE NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by BIGINT,

    CONSTRAINT fk_contract_ptps_contract
        FOREIGN KEY (contract_id)
        REFERENCES contracts(contract_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_contract_ptps_contract_user_latest
    ON contract_ptps(contract_id, created_by, created_at DESC, contract_ptp_id DESC);

CREATE INDEX IF NOT EXISTS idx_contract_ptps_user_date
    ON contract_ptps(created_by, ptp_date);
