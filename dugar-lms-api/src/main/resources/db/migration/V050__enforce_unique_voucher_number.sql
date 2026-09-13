DO $$
DECLARE
    duplicate_numbers TEXT;
BEGIN
    SELECT string_agg(voucher_number || ' (' || duplicate_count || ')', ', ' ORDER BY duplicate_count DESC, voucher_number)
    INTO duplicate_numbers
    FROM (
        SELECT TRIM(voucher_number) AS voucher_number, COUNT(*) AS duplicate_count
        FROM voucher_headers
        GROUP BY TRIM(voucher_number)
        HAVING COUNT(*) > 1
        ORDER BY COUNT(*) DESC, TRIM(voucher_number)
        LIMIT 20
    ) duplicates;

    IF duplicate_numbers IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot add unique voucher number index. Duplicate voucher_number values exist in voucher_headers: %', duplicate_numbers;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_voucher_headers_voucher_number
    ON voucher_headers (TRIM(voucher_number));
