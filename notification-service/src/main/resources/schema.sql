CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    rule_name TEXT,
    rule_description TEXT,
    content TEXT,
    timestamp TIMESTAMP NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_notifications_timestamp ON notifications(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_group_id_timestamp ON notifications(group_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_is_read_timestamp ON notifications(is_read, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_group_id_is_read_timestamp ON notifications(group_id, is_read, timestamp DESC);
