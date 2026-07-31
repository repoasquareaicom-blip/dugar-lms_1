-- Documentation Details fields live on contracts or contract_details.

ALTER TABLE contract_details
    ADD COLUMN IF NOT EXISTS document_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS document_verified_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS guarantor_fi_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tvr_done_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS vehicle_by_agency VARCHAR(100),
    ADD COLUMN IF NOT EXISTS property_valuation_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS legal_opinion_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS branch_collection_tool_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS geo_coordinate_1 VARCHAR(100),
    ADD COLUMN IF NOT EXISTS geo_coordinate_2 VARCHAR(100),
    ADD COLUMN IF NOT EXISTS documents_checked_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS documents_verified_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS disbursed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rc_online_checking VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ho_collection_tool_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ho_tvr_done_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS stock_marked_to_bank VARCHAR(100);
