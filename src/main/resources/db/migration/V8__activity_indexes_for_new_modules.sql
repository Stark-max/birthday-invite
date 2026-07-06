CREATE INDEX IF NOT EXISTS idx_activity_instances_event_slug
    ON activity_instances(event_id, module_slug);

CREATE INDEX IF NOT EXISTS idx_activity_results_instance_guest
    ON activity_results(activity_instance_id, guest_id);

CREATE INDEX IF NOT EXISTS idx_activity_results_instance_points
    ON activity_results(activity_instance_id, points DESC);

CREATE INDEX IF NOT EXISTS idx_activity_results_certificate_code
    ON activity_results ((result_data->>'certificateCode'))
    WHERE result_data ? 'certificateCode';
