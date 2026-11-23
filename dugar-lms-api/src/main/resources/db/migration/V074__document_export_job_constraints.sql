-- Forward-only hardening; keep existing export history and migration checksums.
ALTER TABLE document_export_jobs ADD CONSTRAINT ck_document_export_status
    CHECK(status IN ('QUEUED','RUNNING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED','EXPIRED')) NOT VALID;
ALTER TABLE document_export_items ADD CONSTRAINT ck_document_export_item_status
    CHECK(status IN ('PENDING','SUCCESS','FAILED')) NOT VALID;
CREATE INDEX idx_document_exports_lease ON document_export_jobs(lease_until)
    WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX idx_document_exports_expiry ON document_export_jobs(expires_at)
    WHERE status <> 'EXPIRED';
