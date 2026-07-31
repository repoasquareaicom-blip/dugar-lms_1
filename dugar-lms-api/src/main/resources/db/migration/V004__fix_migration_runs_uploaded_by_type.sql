-- Align existing migration audit tables with the reusable audit framework.
-- Some environments already had migration_runs.uploaded_by as BIGINT.

ALTER TABLE migration_runs
ALTER COLUMN uploaded_by TYPE VARCHAR(100)
USING uploaded_by::VARCHAR;
