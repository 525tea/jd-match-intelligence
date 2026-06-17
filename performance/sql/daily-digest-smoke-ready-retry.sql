-- daily digest mock smoke retry preparation
-- Run this in IntelliJ DB Console after a failed daily digest smoke
-- and before executing MODE=retry performance/notification/daily-digest-mock-smoke.sh.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @digest_type := 'DAILY_DIGEST' COLLATE utf8mb4_unicode_ci;
SET @email_pattern := 'daily-digest-smoke-user-%@example.com' COLLATE utf8mb4_unicode_ci;
SET @app_now := DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 HOUR);

UPDATE notification_logs nl
         JOIN users u ON u.id = nl.user_id
SET nl.next_retry_at = DATE_SUB(@app_now, INTERVAL 1 MINUTE)
WHERE u.email LIKE @email_pattern
  AND nl.type = @digest_type
  AND nl.status = 'PENDING'
  AND nl.attempt_count < nl.max_attempts;

SELECT
    COUNT(*) AS retry_ready_notification_count,
    MIN(nl.next_retry_at) AS earliest_next_retry_at,
    MAX(nl.next_retry_at) AS latest_next_retry_at,
    @app_now AS app_now
FROM notification_logs nl
         JOIN users u ON u.id = nl.user_id
WHERE u.email LIKE @email_pattern
  AND nl.type = @digest_type
  AND nl.status = 'PENDING'
  AND nl.next_retry_at <= @app_now;
