CREATE INDEX IF NOT EXISTS idx_voucher_details_demand_sub_ledger_norm
    ON voucher_details (UPPER(TRIM(sub_ledger_code)), voucher_header_id)
    WHERE sub_ledger_code IS NOT NULL;
