ALTER TABLE users
    ADD COLUMN IF NOT EXISTS user_group VARCHAR(20) NOT NULL DEFAULT 'admin';

UPDATE users
SET user_group = 'admin'
WHERE user_group IS NULL
   OR LOWER(TRIM(user_group)) NOT IN ('admin', 'user');

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_user_group;

ALTER TABLE users
    ADD CONSTRAINT chk_users_user_group
    CHECK (LOWER(TRIM(user_group)) IN ('admin', 'user'));

CREATE INDEX IF NOT EXISTS idx_users_user_group
    ON users (LOWER(TRIM(user_group)));
