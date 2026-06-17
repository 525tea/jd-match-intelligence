-- daily digest mock smoke retry verification
-- Run this in IntelliJ DB Console after executing retry mode.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @digest_type := 'DAILY_DIGEST' COLLATE utf8mb4_unicode_ci;
SET @email_pattern := 'daily-digest-smoke-user-%@example.com' COLLATE utf8mb4_unicode_ci;

SELECT
    COUNT(*) AS notification_log_count,
    SUM(nl.status = 'SENT') AS sent_log_count,
    SUM(nl.status = 'PENDING') AS pending_log_count,
    SUM(nl.status = 'FAILED') AS failed_log_count,
    COUNT(na.id) AS total_attempt_count,
    MIN(nl.attempt_count) AS min_attempt_count,
    MAX(nl.attempt_count) AS max_attempt_count
FROM notification_logs nl
         JOIN users u ON u.id = nl.user_id
         LEFT JOIN notification_attempts na ON na.notification_log_id = nl.id
WHERE u.email LIKE @email_pattern
  AND nl.type = @digest_type;

SELECT
    na.provider,
    na.status,
    na.attempt_number,
    COUNT(*) AS attempt_count
FROM notification_attempts na
         JOIN notification_logs nl ON nl.id = na.notification_log_id
         JOIN users u ON u.id = nl.user_id
WHERE u.email LIKE @email_pattern
  AND nl.type = @digest_type
GROUP BY na.provider, na.status, na.attempt_number
ORDER BY na.attempt_number, na.status;

SELECT
    COUNT(*) AS failed_then_sent_log_count
FROM notification_logs nl
         JOIN users u ON u.id = nl.user_id
WHERE u.email LIKE @email_pattern
  AND nl.type = @digest_type
  AND nl.status = 'SENT'
  AND EXISTS (
      SELECT 1
      FROM notification_attempts failed_attempt
      WHERE failed_attempt.notification_log_id = nl.id
        AND failed_attempt.attempt_number = 1
        AND failed_attempt.status = 'FAILED'
  )
  AND EXISTS (
      SELECT 1
      FROM notification_attempts sent_attempt
      WHERE sent_attempt.notification_log_id = nl.id
        AND sent_attempt.attempt_number = 2
        AND sent_attempt.status = 'SENT'
  );

-- duplicate notification log count. 기대값: 0 rows.
SELECT
    nl.user_id,
    nl.type,
    nl.deduplication_key,
    COUNT(*) AS duplicate_notification_log_count
FROM notification_logs nl
         JOIN users u ON u.id = nl.user_id
WHERE u.email LIKE @email_pattern
  AND nl.type = @digest_type
GROUP BY nl.user_id, nl.type, nl.deduplication_key
HAVING COUNT(*) > 1;

-- duplicate attempt count. 기대값: 0 rows.
SELECT
    na.notification_log_id,
    na.attempt_number,
    COUNT(*) AS duplicate_notification_attempt_count
FROM notification_attempts na
         JOIN notification_logs nl ON nl.id = na.notification_log_id
         JOIN users u ON u.id = nl.user_id
WHERE u.email LIKE @email_pattern
  AND nl.type = @digest_type
GROUP BY na.notification_log_id, na.attempt_number
HAVING COUNT(*) > 1;
