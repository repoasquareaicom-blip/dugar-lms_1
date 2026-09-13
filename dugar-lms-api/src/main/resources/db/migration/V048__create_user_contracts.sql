ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_user_group;

ALTER TABLE users
    ADD CONSTRAINT chk_users_user_group
    CHECK (LOWER(TRIM(user_group)) IN ('admin', 'user', 'customer'));

CREATE TABLE IF NOT EXISTS user_contracts (
    user_contract_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contract_number VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_contracts_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT uq_user_contracts_user_contract UNIQUE (user_id, contract_number)
);

CREATE INDEX IF NOT EXISTS idx_user_contracts_user_id
    ON user_contracts(user_id);

CREATE INDEX IF NOT EXISTS idx_user_contracts_contract_number
    ON user_contracts(UPPER(TRIM(contract_number)));
