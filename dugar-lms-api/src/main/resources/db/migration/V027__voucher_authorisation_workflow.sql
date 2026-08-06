ALTER TABLE voucher_headers
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    ADD COLUMN IF NOT EXISTS submitted_by BIGINT,
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS authorised_by BIGINT,
    ADD COLUMN IF NOT EXISTS authorised_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reopened_by BIGINT,
    ADD COLUMN IF NOT EXISTS reopened_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rejected_by BIGINT,
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
    ADD COLUMN IF NOT EXISTS cancelled_by BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT,
    ADD COLUMN IF NOT EXISTS version_number INTEGER NOT NULL DEFAULT 1;

UPDATE voucher_headers
SET
    status = COALESCE(NULLIF(TRIM(status), ''), 'SUBMITTED'),
    submitted_by = COALESCE(submitted_by, created_by),
    submitted_at = COALESCE(submitted_at, created_at, CURRENT_TIMESTAMP),
    version_number = COALESCE(version_number, 1);

ALTER TABLE voucher_headers
    DROP CONSTRAINT IF EXISTS chk_voucher_headers_status;

ALTER TABLE voucher_headers
    ADD CONSTRAINT chk_voucher_headers_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'AUTHORISED', 'REOPENED', 'REJECTED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_voucher_headers_status
    ON voucher_headers (status);

CREATE INDEX IF NOT EXISTS idx_voucher_headers_authorisation_queue
    ON voucher_headers (status, voucher_date DESC, voucher_header_id DESC);

CREATE TABLE IF NOT EXISTS voucher_header_history (
    voucher_header_history_id BIGSERIAL PRIMARY KEY,
    voucher_header_id BIGINT NOT NULL,
    version_number INTEGER NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    changed_by BIGINT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_reason TEXT,
    header_snapshot JSONB NOT NULL,

    CONSTRAINT fk_voucher_header_history_header_id
        FOREIGN KEY (voucher_header_id)
        REFERENCES voucher_headers (voucher_header_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS voucher_detail_history (
    voucher_detail_history_id BIGSERIAL PRIMARY KEY,
    voucher_header_history_id BIGINT NOT NULL,
    voucher_header_id BIGINT NOT NULL,
    voucher_detail_id BIGINT,
    version_number INTEGER NOT NULL,
    serial_number INTEGER NOT NULL,
    detail_snapshot JSONB NOT NULL,

    CONSTRAINT fk_voucher_detail_history_header_history_id
        FOREIGN KEY (voucher_header_history_id)
        REFERENCES voucher_header_history (voucher_header_history_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_voucher_header_history_header_id
    ON voucher_header_history (voucher_header_id, version_number DESC);

CREATE INDEX IF NOT EXISTS idx_voucher_detail_history_header_history_id
    ON voucher_detail_history (voucher_header_history_id);

WITH transaction_menu AS (
    SELECT menu_id
    FROM menus
    WHERE menu_code = 'TRANSACTION_82'
),
authorisation_menu AS (
    INSERT INTO menus (
        menu_name,
        parent_id,
        url_path,
        icon,
        display_order,
        menu_code,
        menu_type,
        is_active,
        is_visible
    )
    SELECT
        'Voucher Request Sent For Authorization',
        menu_id,
        '/accounts/transaction/voucher-authorisation',
        'ClipboardCheck',
        4,
        'VOUCHER_REQUEST_SENT_FOR_AUTHORIZATION',
        'PAGE',
        TRUE,
        TRUE
    FROM transaction_menu
    ON CONFLICT (menu_code) DO UPDATE
    SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        url_path = EXCLUDED.url_path,
        icon = EXCLUDED.icon,
        display_order = EXCLUDED.display_order,
        menu_type = EXCLUDED.menu_type,
        is_active = TRUE,
        is_visible = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING menu_id
),
admin_roles AS (
    SELECT role_id
    FROM roles
    WHERE is_active = TRUE
      AND (
          UPPER(role_name) = 'SUPER_ADMIN'
          OR UPPER(role_name) = 'ADMIN'
          OR UPPER(role_code) LIKE 'SUPER_ADMIN%'
          OR UPPER(role_code) LIKE 'ADMIN%'
      )
)
INSERT INTO role_permissions (
    role_id,
    menu_id,
    can_view,
    can_add,
    can_edit,
    can_delete
)
SELECT
    admin_roles.role_id,
    authorisation_menu.menu_id,
    TRUE,
    TRUE,
    TRUE,
    TRUE
FROM admin_roles
CROSS JOIN authorisation_menu
ON CONFLICT (role_id, menu_id) DO UPDATE
SET
    can_view = TRUE,
    can_add = TRUE,
    can_edit = TRUE,
    can_delete = TRUE,
    updated_at = CURRENT_TIMESTAMP;
