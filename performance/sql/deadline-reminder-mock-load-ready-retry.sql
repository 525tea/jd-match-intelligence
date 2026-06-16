-- deadline reminder mock load retry preparation
-- Run this in IntelliJ DB Console after a failed mock load run
-- and before executing performance/notification/deadline-reminder-mock-load-smoke.sh with MODE=retry.
--
-- This pulls PENDING notification logs into the retry window from the app-clock point of view.
USE jobflow;

SET @source = 'NOTIFICATION_MOCK_LOAD' COLLATE utf8mb4_unicode_ci;
SET @deadline_reminder_type = 'DEADLINE_REMINDER' COLLATE utf8mb4_unicode_ci;
SET @pending_status = 'PENDING' COLLATE utf8mb4_unicode_ci;
SET @app_now = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 HOUR);

UPDATE notification_logs nl
    JOIN jobs j ON j.id = nl.job_id
    SET nl.next_retry_at = DATE_SUB(@app_now, INTERVAL 1 MINUTE),
        nl.updated_at = NOW(6)
WHERE j.source = @source
  AND nl.type = @deadline_reminder_type
  AND nl.status = @pending_status
  AND nl.attempt_count < nl.max_attempts;

SELECT
    COUNT(*) AS retry_ready_notification_count,
    MIN(nl.next_retry_at) AS earliest_next_retry_at,
    MAX(nl.next_retry_at) AS latest_next_retry_at,
    @app_now AS app_now
FROM notification_logs nl
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source
  AND nl.type = @deadline_reminder_type
  AND nl.status = @pending_status
  AND nl.next_retry_at <= @app_now
  AND nl.attempt_count < nl.max_attempts;
