CREATE TABLE IF NOT EXISTS user_roles (
    user_role_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id)
        REFERENCES roles(role_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT uq_user_roles_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user_id
    ON user_roles(user_id);

CREATE INDEX IF NOT EXISTS idx_user_roles_role_id
    ON user_roles(role_id);

INSERT INTO user_roles (user_id, role_id)
SELECT user_id, role_id
FROM users
WHERE role_id IS NOT NULL
ON CONFLICT (user_id, role_id) DO NOTHING;

UPDATE menus
SET
    url_path = '/committee/masters/user-management',
    updated_at = CURRENT_TIMESTAMP
WHERE UPPER(TRIM(menu_name)) = 'USER MANAGEMENT'
  AND COALESCE(url_path, '#') = '#';
