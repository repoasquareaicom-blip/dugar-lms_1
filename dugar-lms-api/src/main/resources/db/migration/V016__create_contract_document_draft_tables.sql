CREATE TABLE IF NOT EXISTS contract_pending_documents (
    pending_document_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    document_name VARCHAR(150) NOT NULL,
    is_pending BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP,
    updated_by VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_contract_pending_documents_contract_id
        FOREIGN KEY (contract_id)
        REFERENCES contracts (contract_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_contract_pending_documents_contract_name
        UNIQUE (contract_id, document_name)
);

CREATE TABLE IF NOT EXISTS contract_document_uploads (
    document_upload_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    document_category VARCHAR(150) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    content_type VARCHAR(150),
    storage_path TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP,
    updated_by VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_contract_document_uploads_contract_id
        FOREIGN KEY (contract_id)
        REFERENCES contracts (contract_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_contract_pending_documents_contract_id
    ON contract_pending_documents (contract_id);

CREATE INDEX IF NOT EXISTS idx_contract_document_uploads_contract_id
    ON contract_document_uploads (contract_id);
