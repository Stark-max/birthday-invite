CREATE TABLE admin_users (
    id BIGSERIAL PRIMARY KEY,
    login VARCHAR(120) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    CONSTRAINT chk_admin_users_role CHECK (role IN ('SUPER_ADMIN', 'ADMIN'))
);

ALTER TABLE events
    ADD COLUMN owner_admin_id BIGINT REFERENCES admin_users(id) ON DELETE RESTRICT,
    ADD COLUMN public_slug VARCHAR(140);

ALTER TABLE events
    ALTER COLUMN password_hash DROP NOT NULL;

INSERT INTO admin_users (login, display_name, password_hash, role, enabled, must_change_password)
SELECT 'superadmin', 'Super Admin', e.password_hash, 'SUPER_ADMIN', TRUE, FALSE
FROM events e
WHERE e.password_hash IS NOT NULL
ORDER BY e.id
LIMIT 1;

UPDATE events
SET owner_admin_id = (SELECT id FROM admin_users WHERE login = 'superadmin')
WHERE owner_admin_id IS NULL
  AND EXISTS (SELECT 1 FROM admin_users WHERE login = 'superadmin');

UPDATE events
SET public_slug = 'event-' || id
WHERE public_slug IS NULL;

CREATE UNIQUE INDEX uq_events_public_slug ON events(public_slug);
CREATE INDEX idx_events_owner_admin ON events(owner_admin_id);

