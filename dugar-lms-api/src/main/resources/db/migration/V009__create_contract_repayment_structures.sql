-- Repayment slab structures for migrated contracts.

CREATE TABLE IF NOT EXISTS contract_repayment_structures (
    repayment_structure_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    sequence_no INTEGER NOT NULL,
    number_of_installments INTEGER NOT NULL,
    installment_amount NUMERIC(18,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_contract_repayment_structures_contract_id
        FOREIGN KEY (contract_id)
        REFERENCES contracts (contract_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_contract_repayment_structures_contract_sequence
        UNIQUE (contract_id, sequence_no),
    CONSTRAINT chk_contract_repayment_structures_sequence_no
        CHECK (sequence_no > 0),
    CONSTRAINT chk_contract_repayment_structures_installment_count
        CHECK (number_of_installments > 0),
    CONSTRAINT chk_contract_repayment_structures_installment_amount
        CHECK (installment_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_contract_repayment_structures_contract_id
    ON contract_repayment_structures (contract_id);

CREATE INDEX IF NOT EXISTS idx_contract_repayment_structures_sequence_no
    ON contract_repayment_structures (sequence_no);
