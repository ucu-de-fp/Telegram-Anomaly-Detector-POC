CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    keyword VARCHAR(100),
    content TEXT,
    timestamp TIMESTAMP NOT NULL
);

ALTER TABLE IF EXISTS notifications DROP COLUMN IF EXISTS latitude;
ALTER TABLE IF EXISTS notifications DROP COLUMN IF EXISTS longitude;
ALTER TABLE IF EXISTS notifications ADD COLUMN IF NOT EXISTS group_id BIGINT;
ALTER TABLE IF EXISTS notifications DROP COLUMN IF EXISTS group_name;
ALTER TABLE IF EXISTS notifications DROP COLUMN IF EXISTS group_link;

CREATE INDEX IF NOT EXISTS idx_notifications_timestamp ON notifications(timestamp DESC);
DROP INDEX IF EXISTS idx_notifications_group_link_timestamp;
CREATE INDEX IF NOT EXISTS idx_notifications_group_id_timestamp ON notifications(group_id, timestamp DESC);
