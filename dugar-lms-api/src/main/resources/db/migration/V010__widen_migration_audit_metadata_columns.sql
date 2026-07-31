-- Widen migration audit metadata fields so new migration names and files fit
-- without truncating source values in Java.

ALTER TABLE migration_runs
    ALTER COLUMN migration_type TYPE VARCHAR(50),
    ALTER COLUMN file_name TYPE VARCHAR(255),
    ALTER COLUMN sheet_name TYPE VARCHAR(255),
    ALTER COLUMN status TYPE VARCHAR(50),
    ALTER COLUMN uploaded_by TYPE VARCHAR(255);

ALTER TABLE migration_run_details
    ALTER COLUMN reference_key TYPE VARCHAR(255),
    ALTER COLUMN result_type TYPE VARCHAR(50),
    ALTER COLUMN reason TYPE TEXT,
    ALTER COLUMN source_data TYPE TEXT;
