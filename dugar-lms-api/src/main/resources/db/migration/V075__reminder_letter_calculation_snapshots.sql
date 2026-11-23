-- Durable grid snapshots avoid recalculating ageing on page/search changes.
-- Export values are frozen before translation/rendering releases the calculation connection.
CREATE TABLE document_reminder_snapshots (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    as_on_date DATE NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '30 minutes'
);
CREATE INDEX idx_document_reminder_expiry ON document_reminder_snapshots(expires_at);
CREATE TABLE document_reminder_rows (
    snapshot_id UUID NOT NULL REFERENCES document_reminder_snapshots(id) ON DELETE CASCADE,
    contract_id BIGINT NOT NULL,
    contract_number TEXT NOT NULL,
    document_values JSONB NOT NULL,
    PRIMARY KEY(snapshot_id,contract_id)
);
ALTER TABLE document_export_items ADD COLUMN document_values JSONB;
