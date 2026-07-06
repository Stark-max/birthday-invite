CREATE TABLE activity_instances (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    module_slug VARCHAR(120) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    config JSONB NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_activity_instances_event_enabled_sort
    ON activity_instances(event_id, is_enabled, sort_order);

CREATE TABLE activity_results (
    id BIGSERIAL PRIMARY KEY,
    activity_instance_id BIGINT NOT NULL REFERENCES activity_instances(id) ON DELETE CASCADE,
    guest_id BIGINT NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    points INT NOT NULL DEFAULT 0,
    result_data JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_activity_results_instance ON activity_results(activity_instance_id);
CREATE INDEX idx_activity_results_guest ON activity_results(guest_id);
