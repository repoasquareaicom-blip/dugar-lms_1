ALTER TABLE contract_details
    ADD COLUMN IF NOT EXISTS co_lending_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS co_lender_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS co_lending_security_deposit NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS co_lending_contribution_share_percent NUMERIC(7,4),
    ADD COLUMN IF NOT EXISTS co_lending_emi_share_percent NUMERIC(7,4),
    ADD COLUMN IF NOT EXISTS co_lending_revenue_share_percent NUMERIC(7,4),
    ADD COLUMN IF NOT EXISTS co_lending_risk_share_percent NUMERIC(7,4);
