CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    event_date DATE NOT NULL,
    event_time VARCHAR(32),
    location VARCHAR(500),
    location_url VARCHAR(1000),
    message TEXT,
    contact_info VARCHAR(500),
    password_hash VARCHAR(255) NOT NULL,
    show_guest_list BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE guests (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    code VARCHAR(16) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL,
    guest_name VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    wish TEXT,
    responded_at TIMESTAMP
);

CREATE INDEX idx_guests_event_status ON guests(event_id, status);
CREATE INDEX idx_guests_code ON guests(code);

CREATE TABLE wishlist_items (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_wishlist_items_event_sort ON wishlist_items(event_id, sort_order);
