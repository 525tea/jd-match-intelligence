-- deadline reminder mock load verification
-- Run this in IntelliJ DB Console after executing performance/notification/deadline-reminder-mock-load-smoke.sh.

SET @source = 'NOTIFICATION_MOCK_LOAD' COLLATE utf8mb4_unicode_ci;
SET @mock_user_email_pattern = 'deadline-mock-user-%@example.com' COLLATE utf8mb4_unicode_ci;
SET @deadline_reminder_type = 'DEADLINE_REMINDER' COLLATE utf8mb4_unicode_ci;
SET @saved_status = 'SAVED' COLLATE utf8mb4_unicode_ci;
SET @open_status = 'OPEN' COLLATE utf8mb4_unicode_ci;
SET @app_now = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 HOUR);

-- 1) fixture 규모
SELECT
    COUNT(DISTINCT u.id) AS mock_user_count,
    COUNT(DISTINCT j.id) AS mock_job_count,
    COUNT(uj.id) AS mock_saved_user_job_count
FROM users u
         JOIN user_jobs uj ON uj.user_id = u.id
         JOIN jobs j ON j.id = uj.job_id
WHERE u.email LIKE @mock_user_email_pattern
  AND j.source = @source;

-- 2) 아직 발송 후보로 남은 건수
SELECT
    COUNT(*) AS remaining_due_soon_candidate_count
FROM user_jobs uj
         JOIN users u ON u.id = uj.user_id
         JOIN jobs j ON j.id = uj.job_id
WHERE u.email LIKE @mock_user_email_pattern
  AND j.source = @source
  AND uj.status = @saved_status
  AND j.status = @open_status
  AND j.deadline_at IS NOT NULL
  AND j.deadline_at > @app_now
  AND j.deadline_at <= DATE_ADD(@app_now, INTERVAL 24 HOUR)
  AND NOT EXISTS (
      SELECT 1
      FROM notification_logs nl
      WHERE nl.user_id = uj.user_id
        AND nl.job_id = uj.job_id
        AND nl.type = @deadline_reminder_type
  );

-- 3) notification log 상태별 집계
SELECT
    nl.type,
    nl.status,
    COUNT(*) AS notification_log_count,
    SUM(nl.attempt_count) AS total_attempt_count,
    MIN(nl.next_retry_at) AS earliest_next_retry_at,
    MAX(nl.sent_at) AS latest_sent_at
FROM notification_logs nl
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source
  AND nl.type = @deadline_reminder_type
GROUP BY nl.type, nl.status
ORDER BY nl.type, nl.status;

-- 4) notification attempt 상태별 집계
SELECT
    na.provider,
    na.status,
    COUNT(*) AS notification_attempt_count,
    MIN(na.attempted_at) AS first_attempted_at,
    MAX(na.attempted_at) AS last_attempted_at
FROM notification_attempts na
         JOIN notification_logs nl ON nl.id = na.notification_log_id
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source
GROUP BY na.provider, na.status
ORDER BY na.provider, na.status;

-- 5) duplicate notification log count. 기대값: 0 rows.
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

-- 6) duplicate attempt count. 기대값: 0 rows.
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

-- 7) mock load summary
SELECT
    fixture.mock_saved_user_job_count,
    COALESCE(logs.notification_log_count, 0) AS notification_log_count,
    COALESCE(attempts.notification_attempt_count, 0) AS notification_attempt_count,
    COALESCE(sent_logs.sent_log_count, 0) AS sent_log_count,
    COALESCE(failed_attempts.failed_attempt_count, 0) AS failed_attempt_count,
    COALESCE(remaining.remaining_due_soon_candidate_count, 0) AS remaining_due_soon_candidate_count
FROM (
    SELECT COUNT(uj.id) AS mock_saved_user_job_count
    FROM users u
             JOIN user_jobs uj ON uj.user_id = u.id
             JOIN jobs j ON j.id = uj.job_id
    WHERE u.email LIKE @mock_user_email_pattern
      AND j.source = @source
) fixture
         CROSS JOIN (
    SELECT COUNT(*) AS notification_log_count
    FROM notification_logs nl
             JOIN jobs j ON j.id = nl.job_id
    WHERE j.source = @source
      AND nl.type = @deadline_reminder_type
) logs
         CROSS JOIN (
    SELECT COUNT(*) AS notification_attempt_count
    FROM notification_attempts na
             JOIN notification_logs nl ON nl.id = na.notification_log_id
             JOIN jobs j ON j.id = nl.job_id
    WHERE j.source = @source
) attempts
         CROSS JOIN (
    SELECT COUNT(*) AS sent_log_count
    FROM notification_logs nl
             JOIN jobs j ON j.id = nl.job_id
    WHERE j.source = @source
      AND nl.type = @deadline_reminder_type
      AND nl.status = 'SENT'
) sent_logs
         CROSS JOIN (
    SELECT COUNT(*) AS failed_attempt_count
    FROM notification_attempts na
             JOIN notification_logs nl ON nl.id = na.notification_log_id
             JOIN jobs j ON j.id = nl.job_id
    WHERE j.source = @source
      AND na.status = 'FAILED'
) failed_attempts
         CROSS JOIN (
    SELECT COUNT(*) AS remaining_due_soon_candidate_count
    FROM user_jobs uj
             JOIN users u ON u.id = uj.user_id
             JOIN jobs j ON j.id = uj.job_id
    WHERE u.email LIKE @mock_user_email_pattern
      AND j.source = @source
      AND uj.status = @saved_status
      AND j.status = @open_status
      AND j.deadline_at IS NOT NULL
      AND j.deadline_at > @app_now
      AND j.deadline_at <= DATE_ADD(@app_now, INTERVAL 24 HOUR)
      AND NOT EXISTS (
          SELECT 1
          FROM notification_logs nl
          WHERE nl.user_id = uj.user_id
            AND nl.job_id = uj.job_id
            AND nl.type = @deadline_reminder_type
      )
) remaining;
