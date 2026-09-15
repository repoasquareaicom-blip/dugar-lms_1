CREATE TABLE IF NOT EXISTS contract_follow_ups (
    contract_follow_up_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    comment_text TEXT NOT NULL,
    follow_up_date DATE,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_contract_follow_ups_contract
        FOREIGN KEY (contract_id)
        REFERENCES contracts(contract_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_contract_follow_ups_contract_created
    ON contract_follow_ups(contract_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_contract_follow_ups_follow_up_date
    ON contract_follow_ups(follow_up_date);
