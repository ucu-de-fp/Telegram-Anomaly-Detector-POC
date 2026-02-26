CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    group_name VARCHAR(255) NOT NULL,
    group_link VARCHAR(500) NOT NULL,
    keyword VARCHAR(100),
    content TEXT,
    timestamp TIMESTAMP NOT NULL
);

ALTER TABLE IF EXISTS notifications DROP COLUMN IF EXISTS latitude;
ALTER TABLE IF EXISTS notifications DROP COLUMN IF EXISTS longitude;

CREATE INDEX IF NOT EXISTS idx_notifications_timestamp ON notifications(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_group_link_timestamp ON notifications(group_link, timestamp DESC);
