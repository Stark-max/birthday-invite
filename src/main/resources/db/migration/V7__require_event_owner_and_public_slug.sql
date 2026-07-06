ALTER TABLE events
    ALTER COLUMN owner_admin_id SET NOT NULL,
    ALTER COLUMN public_slug SET NOT NULL;

