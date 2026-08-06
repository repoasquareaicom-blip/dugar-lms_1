ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS los_received BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS los_proposal_id BIGINT,
    ADD COLUMN IF NOT EXISTS los_agent_request_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS los_received_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uq_contracts_los_proposal_id
    ON contracts (los_proposal_id)
    WHERE los_proposal_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_contracts_los_received
    ON contracts (los_received);
