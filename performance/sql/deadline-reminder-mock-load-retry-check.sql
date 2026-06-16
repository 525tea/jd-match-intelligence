-- deadline reminder mock load retry verification
-- Run this in IntelliJ DB Console after executing failed due-soon smoke,
-- ready-retry SQL, and successful retry smoke.
USE jobflow;

SET @source = 'NOTIFICATION_MOCK_LOAD' COLLATE utf8mb4_unicode_ci;
SET @deadline_reminder_type = 'DEADLINE_REMINDER' COLLATE utf8mb4_unicode_ci;

-- 1) retry 최종 상태 요약
SELECT
    COUNT(*) AS notification_log_count,
    SUM(CASE WHEN nl.status = 'SENT' THEN 1 ELSE 0 END) AS sent_log_count,
    SUM(CASE WHEN nl.status = 'PENDING' THEN 1 ELSE 0 END) AS pending_log_count,
    SUM(CASE WHEN nl.status = 'FAILED' THEN 1 ELSE 0 END) AS failed_log_count,
    SUM(nl.attempt_count) AS total_attempt_count,
    MIN(nl.attempt_count) AS min_attempt_count,
    MAX(nl.attempt_count) AS max_attempt_count
FROM notification_logs nl
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source
  AND nl.type = @deadline_reminder_type;

-- 2) attempt 상태별 집계
SELECT
    na.provider,
    na.status,
    na.attempt_number,
    COUNT(*) AS attempt_count
FROM notification_attempts na
         JOIN notification_logs nl ON nl.id = na.notification_log_id
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source
  AND nl.type = @deadline_reminder_type
GROUP BY na.provider, na.status, na.attempt_number
ORDER BY na.provider, na.attempt_number, na.status;

-- 3) 실패 후 retry 성공한 로그 수
SELECT
    COUNT(*) AS failed_then_sent_log_count
FROM notification_logs nl
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source
  AND nl.type = @deadline_reminder_type
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

-- 4) duplicate notification log count. 기대값: 0 rows.
SELECT
    nl.user_id,
    nl.job_id,
    nl.type,
    COUNT(*) AS duplicate_notification_log_count
FROM notification_logs nl
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source
GROUP BY nl.user_id, nl.job_id, nl.type
HAVING COUNT(*) > 1;

-- 5) duplicate attempt count. 기대값: 0 rows.
SELECT
    na.notification_log_id,
    na.attempt_number,
    COUNT(*) AS duplicate_notification_attempt_count
FROM notification_attempts na
         JOIN notification_logs nl ON nl.id = na.notification_log_id
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source
GROUP BY na.notification_log_id, na.attempt_number
HAVING COUNT(*) > 1;
