ALTER TABLE wishlist_items
    ADD COLUMN reserved_by_guest_id BIGINT REFERENCES guests(id) ON DELETE SET NULL,
    ADD COLUMN reserved_at TIMESTAMP;

CREATE INDEX idx_wishlist_items_reserved_by_guest ON wishlist_items(reserved_by_guest_id);
