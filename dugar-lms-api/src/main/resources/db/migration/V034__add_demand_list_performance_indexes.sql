CREATE INDEX IF NOT EXISTS idx_contracts_demand_active_status_area
    ON contracts (area_code, contract_id)
    WHERE is_active = TRUE
      AND UPPER(TRIM(COALESCE(status, ''))) = 'Y';

CREATE INDEX IF NOT EXISTS idx_contracts_demand_contract_number_norm
    ON contracts (UPPER(TRIM(contract_number)))
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_contracts_demand_legacy_contract_number_norm
    ON contracts (UPPER(TRIM(legacy_contract_number)))
    WHERE is_active = TRUE
      AND legacy_contract_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_assets_contract_asset_id
    ON assets (contract_id, asset_id);

CREATE INDEX IF NOT EXISTS idx_contract_details_active_contract
    ON contract_details (contract_id)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_voucher_headers_demand_authorised_contract
    ON voucher_headers (contract_id, voucher_date)
    WHERE UPPER(TRIM(COALESCE(status, ''))) = 'AUTHORISED';

CREATE INDEX IF NOT EXISTS idx_voucher_headers_demand_authorised_contract_number_norm
    ON voucher_headers (UPPER(TRIM(contract_number)), voucher_date)
    WHERE UPPER(TRIM(COALESCE(status, ''))) = 'AUTHORISED'
      AND contract_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_voucher_details_demand_ledger_header
    ON voucher_details (TRIM(COALESCE(ledger_code, '')), voucher_header_id);

CREATE INDEX IF NOT EXISTS idx_voucher_details_demand_loan_reference_norm
    ON voucher_details (UPPER(TRIM(loan_reference)), voucher_header_id)
    WHERE loan_reference IS NOT NULL;
