-- Store legacy EQUP_MODEL exactly as text values such as "SWIFT DZIR", "02/2015", or "2019*".

ALTER TABLE contracts
ADD COLUMN IF NOT EXISTS equipment_model VARCHAR(100);

ALTER TABLE contracts
ALTER COLUMN equipment_model TYPE VARCHAR(100)
USING equipment_model::VARCHAR;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'contracts'
          AND column_name = 'manufacturing_year'
    ) THEN
        EXECUTE '
            UPDATE contracts
            SET equipment_model = manufacturing_year::VARCHAR
            WHERE equipment_model IS NULL
              AND manufacturing_year IS NOT NULL
        ';
    END IF;
END $$;
