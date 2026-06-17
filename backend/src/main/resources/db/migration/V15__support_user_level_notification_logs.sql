ALTER TABLE notification_logs
    ADD COLUMN deduplication_key VARCHAR(100) NULL AFTER type;

UPDATE notification_logs
SET deduplication_key = CONCAT(type, ':job:', job_id)
WHERE deduplication_key IS NULL;

ALTER TABLE notification_logs
    MODIFY COLUMN deduplication_key VARCHAR(100) NOT NULL;

ALTER TABLE notification_logs
    DROP INDEX uk_notification_logs_user_job_type;

ALTER TABLE notification_logs
    ADD UNIQUE KEY uk_notification_logs_user_type_key (user_id, type, deduplication_key);

ALTER TABLE notification_logs
    MODIFY COLUMN job_id BIGINT NULL;
