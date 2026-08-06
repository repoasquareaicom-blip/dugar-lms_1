CREATE TABLE IF NOT EXISTS ledger_codes (
    ledger_id BIGSERIAL PRIMARY KEY,
    ledger_code VARCHAR(100) NOT NULL,
    ledger_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(50),
    schedule_debit_sub_code VARCHAR(100),
    schedule_credit_sub_code VARCHAR(100),
    trial_balance_major_code VARCHAR(100),
    trial_balance_minor_code VARCHAR(100),
    pl_bs_flag VARCHAR(20),
    has_sub_ledger BOOLEAN NOT NULL DEFAULT FALSE,
    link_reference BOOLEAN NOT NULL DEFAULT FALSE,
    legacy_user_id VARCHAR(100),
    legacy_user_doc DATE,
    source_system VARCHAR(50) NOT NULL DEFAULT 'ACCMAS',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ledger_codes_code UNIQUE (ledger_code)
);

CREATE INDEX IF NOT EXISTS idx_ledger_codes_name
    ON ledger_codes (ledger_name);

CREATE INDEX IF NOT EXISTS idx_ledger_codes_active
    ON ledger_codes (is_active);

UPDATE menus
SET
    menu_name = 'Ledger Code',
    url_path = '/accounts/masters/ledger-code',
    icon = 'FileText',
    is_active = TRUE,
    is_visible = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'LEDGER_CODE_85';

INSERT INTO role_permissions (
    role_id,
    menu_id,
    can_view,
    can_add,
    can_edit,
    can_delete
)
SELECT
    roles.role_id,
    menus.menu_id,
    TRUE,
    TRUE,
    TRUE,
    TRUE
FROM roles
JOIN menus
  ON menus.menu_code = 'LEDGER_CODE_85'
WHERE roles.is_active = TRUE
  AND (
      UPPER(roles.role_name) IN ('SUPER_ADMIN', 'ADMIN')
      OR UPPER(roles.role_code) LIKE 'SUPER_ADMIN%'
      OR UPPER(roles.role_code) LIKE 'ADMIN%'
  )
ON CONFLICT (role_id, menu_id) DO UPDATE
SET
    can_view = TRUE,
    can_add = TRUE,
    can_edit = TRUE,
    can_delete = TRUE,
    updated_at = CURRENT_TIMESTAMP;
