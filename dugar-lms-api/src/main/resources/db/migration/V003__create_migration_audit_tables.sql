-- Generic migration audit tables shared by current and future migration modules.

CREATE TABLE IF NOT EXISTS migration_runs (
    migration_run_id BIGSERIAL PRIMARY KEY,
    migration_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    sheet_name VARCHAR(100),
    total_rows INTEGER NOT NULL DEFAULT 0,
    inserted_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    uploaded_by VARCHAR(100),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS migration_run_details (
    migration_run_detail_id BIGSERIAL PRIMARY KEY,
    migration_run_id BIGINT NOT NULL,
    excel_row INTEGER,
    reference_key VARCHAR(255),
    result_type VARCHAR(20) NOT NULL,
    reason TEXT,
    source_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_migration_run_details_run_id
        FOREIGN KEY (migration_run_id)
        REFERENCES migration_runs (migration_run_id)
);

CREATE INDEX IF NOT EXISTS idx_migration_runs_migration_type
    ON migration_runs (migration_type);

CREATE INDEX IF NOT EXISTS idx_migration_runs_status
    ON migration_runs (status);

CREATE INDEX IF NOT EXISTS idx_migration_run_details_run_id
    ON migration_run_details (migration_run_id);

CREATE INDEX IF NOT EXISTS idx_migration_run_details_result_type
    ON migration_run_details (result_type);
