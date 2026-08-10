ALTER TABLE voucher_details
    ADD COLUMN IF NOT EXISTS voucher_type VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_voucher_details_voucher_type
    ON voucher_details (voucher_type);
