-- Reuse word_templates; these tables track bounded asynchronous exports, not template masters.
CREATE TABLE document_export_jobs (
    id UUID PRIMARY KEY, template_id BIGINT NOT NULL REFERENCES word_templates(id),
    template_name VARCHAR(150) NOT NULL, language_code VARCHAR(16) NOT NULL,
    template_hash CHAR(64) NOT NULL, output_format VARCHAR(4) NOT NULL CHECK(output_format IN ('DOCX','PDF')),
    all_matching BOOLEAN NOT NULL, selection JSONB NOT NULL,
    user_id BIGINT NOT NULL, username VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED', total_count BIGINT NOT NULL DEFAULT 0,
    success_count BIGINT NOT NULL DEFAULT 0, failure_count BIGINT NOT NULL DEFAULT 0,
    artifact_size BIGINT, artifact_path TEXT, artifact_name VARCHAR(500), failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '20 minutes'),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours')
);
CREATE TABLE document_export_items (
    job_id UUID NOT NULL REFERENCES document_export_jobs(id) ON DELETE CASCADE,
    contract_id BIGINT NOT NULL, contract_number VARCHAR(150),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', generation_id UUID REFERENCES document_generation_audit(id),
    filename VARCHAR(500), error_message VARCHAR(500), PRIMARY KEY(job_id,contract_id)
);
CREATE INDEX idx_document_exports_owner ON document_export_jobs(user_id,created_at DESC);
CREATE INDEX idx_document_export_pending ON document_export_items(job_id,status,contract_id);

-- Preserve pre-existing history (including any orphan rows) while protecting future references and deletion races.
ALTER TABLE document_generation_audit ADD CONSTRAINT fk_document_audit_template
    FOREIGN KEY(template_id) REFERENCES word_templates(id) NOT VALID;
CREATE INDEX idx_document_audit_template ON document_generation_audit(template_id);
