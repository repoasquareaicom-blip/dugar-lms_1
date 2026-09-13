ALTER TABLE users
    ADD COLUMN IF NOT EXISTS user_type VARCHAR(20);

UPDATE users
SET user_type = CASE
    WHEN LOWER(TRIM(COALESCE(user_group, ''))) = 'customer' THEN 'CUSTOMER'
    ELSE 'USER'
END
WHERE user_type IS NULL
   OR TRIM(user_type) = '';

UPDATE users
SET user_group = 'user'
WHERE LOWER(TRIM(COALESCE(user_group, ''))) = 'customer';

ALTER TABLE users
    ALTER COLUMN user_type SET DEFAULT 'USER';

ALTER TABLE users
    ALTER COLUMN user_type SET NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_user_type;

ALTER TABLE users
    ADD CONSTRAINT chk_users_user_type
    CHECK (UPPER(TRIM(user_type)) IN ('USER', 'BRANCH', 'STATE', 'CUSTOMER'));

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_user_group;

ALTER TABLE users
    ADD CONSTRAINT chk_users_user_group
    CHECK (LOWER(TRIM(user_group)) IN ('admin', 'user'));

CREATE INDEX IF NOT EXISTS idx_users_user_type
    ON users (UPPER(TRIM(user_type)));

CREATE TABLE IF NOT EXISTS user_areas (
    user_area_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    area_code VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_areas_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT uq_user_areas_user_area UNIQUE (user_id, area_code)
);

CREATE INDEX IF NOT EXISTS idx_user_areas_user_id
    ON user_areas(user_id);

CREATE INDEX IF NOT EXISTS idx_user_areas_area_code
    ON user_areas(UPPER(TRIM(area_code)));
