CREATE TABLE IF NOT EXISTS contract_flag_master (
    contract_flag_master_id BIGSERIAL PRIMARY KEY,
    flag_code VARCHAR(50) NOT NULL UNIQUE,
    flag_name VARCHAR(150) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

INSERT INTO contract_flag_master (flag_code, flag_name, display_order, is_active, created_by)
VALUES
    ('VEHICLE_MISSING', 'Vehicle Missing', 1, TRUE, 'system'),
    ('CUSTOMER_ABSCONDING', 'Customer Absconding', 2, TRUE, 'system'),
    ('CUSTOMER_EXPIRED', 'Customer Expired', 3, TRUE, 'system'),
    ('VEHICLE_ACCIDENT', 'Vehicle Accident', 4, TRUE, 'system'),
    ('CUSTOMER_INFLUENCING', 'Customer Influencing', 5, TRUE, 'system'),
    ('ANY_OTHER', 'Any Other', 6, TRUE, 'system'),
    ('VEHICLE_CONDITION_REPORT', 'Vehicle Condition Report', 7, TRUE, 'system')
ON CONFLICT (flag_code) DO UPDATE
SET flag_name = EXCLUDED.flag_name,
    display_order = EXCLUDED.display_order,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system';

CREATE TABLE IF NOT EXISTS contract_flags (
    contract_flag_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    contract_flag_master_id BIGINT NOT NULL,
    remarks VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_contract_flags_contract
        FOREIGN KEY (contract_id) REFERENCES contracts(contract_id),
    CONSTRAINT fk_contract_flags_master
        FOREIGN KEY (contract_flag_master_id) REFERENCES contract_flag_master(contract_flag_master_id),
    CONSTRAINT uq_contract_flags_contract_master
        UNIQUE (contract_id, contract_flag_master_id)
);

CREATE INDEX IF NOT EXISTS idx_contract_flags_contract_id
    ON contract_flags(contract_id);

CREATE INDEX IF NOT EXISTS idx_contract_flags_master_id
    ON contract_flags(contract_flag_master_id);
