-- =========================================================
-- DUGAR LMS - CORE TABLES
-- Must run before V001 and later migrations
-- =========================================================

-- ---------------------------------------------------------
-- Common updated_at trigger function
-- ---------------------------------------------------------
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


-- ---------------------------------------------------------
-- ROLES
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.roles
(
    role_id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(255) NOT NULL,
    role_code VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_roles_role_name UNIQUE (role_name),
    CONSTRAINT uq_roles_role_code UNIQUE (role_code)
);

CREATE OR REPLACE TRIGGER trg_roles_set_updated_at
    BEFORE UPDATE ON public.roles
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();


-- ---------------------------------------------------------
-- USERS
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.users
(
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    email_id VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT users_username_key UNIQUE (username),
    CONSTRAINT users_email_id_key UNIQUE (email_id),

    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id)
        REFERENCES public.roles(role_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_users_is_active
    ON public.users(is_active);

CREATE INDEX IF NOT EXISTS idx_users_role_id
    ON public.users(role_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username_lower
    ON public.users(lower(username));

CREATE OR REPLACE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON public.users
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();


-- ---------------------------------------------------------
-- MENUS
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.menus
(
    menu_id BIGSERIAL PRIMARY KEY,
    menu_name VARCHAR(255) NOT NULL,
    parent_id BIGINT,
    url_path VARCHAR(255),
    icon VARCHAR(255),
    display_order INTEGER NOT NULL DEFAULT 0,
    menu_code VARCHAR(150) NOT NULL,
    menu_type VARCHAR(20) NOT NULL DEFAULT 'PAGE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_menus_menu_code UNIQUE (menu_code),

    CONSTRAINT fk_menus_parent
        FOREIGN KEY (parent_id)
        REFERENCES public.menus(menu_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_menus_menu_type
        CHECK (menu_type IN ('MODULE', 'MENU', 'PAGE'))
);

CREATE INDEX IF NOT EXISTS idx_menus_active_visible
    ON public.menus(is_active, is_visible);

CREATE INDEX IF NOT EXISTS idx_menus_display_order
    ON public.menus(display_order);

CREATE INDEX IF NOT EXISTS idx_menus_parent_id
    ON public.menus(parent_id);


-- ---------------------------------------------------------
-- ROLE PERMISSIONS
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.role_permissions
(
    permission_id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT FALSE,
    can_add BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_role_permissions_role_menu
        UNIQUE (role_id, menu_id),

    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id)
        REFERENCES public.roles(role_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_role_permissions_menu
        FOREIGN KEY (menu_id)
        REFERENCES public.menus(menu_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role_id
    ON public.role_permissions(role_id);

CREATE INDEX IF NOT EXISTS idx_role_permissions_menu_id
    ON public.role_permissions(menu_id);