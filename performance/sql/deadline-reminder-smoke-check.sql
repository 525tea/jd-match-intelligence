-- deadline reminder batch verification
-- Run this in IntelliJ DB Console after executing the deadline reminder batch smoke.

-- 1) 현재 시점 기준 마감 알림 후보 수
SELECT
    COUNT(*) AS due_soon_saved_open_job_count
FROM user_jobs uj
         JOIN users u ON u.id = uj.user_id
         JOIN jobs j ON j.id = uj.job_id
WHERE uj.status = 'SAVED'
  AND j.status = 'OPEN'
  AND j.deadline_at IS NOT NULL
  AND j.deadline_at > NOW()
  AND j.deadline_at <= DATE_ADD(NOW(), INTERVAL 24 HOUR)
  AND NOT EXISTS (
      SELECT 1
      FROM notification_logs nl
      WHERE nl.user_id = uj.user_id
        AND nl.job_id = uj.job_id
        AND nl.type = 'DEADLINE_REMINDER'
  );

-- 2) 알림 로그 상태별 집계
SELECT
    type,
    status,
    COUNT(*) AS notification_log_count,
    SUM(attempt_count) AS total_attempt_count,
    MIN(next_retry_at) AS earliest_next_retry_at,
    MAX(sent_at) AS latest_sent_at
FROM notification_logs
WHERE type = 'DEADLINE_REMINDER'
GROUP BY type, status
ORDER BY type, status;

-- 3) 발송 시도 상태별 집계
SELECT
    provider,
    status,
    COUNT(*) AS notification_attempt_count,
    MIN(attempted_at) AS first_attempted_at,
    MAX(attempted_at) AS last_attempted_at
FROM notification_attempts
GROUP BY provider, status
ORDER BY provider, status;

-- 4) 최근 마감 알림 상세
SELECT
    nl.id AS notification_log_id,
    nl.user_id,
    u.email,
    nl.job_id,
    j.title,
    j.company_name,
    j.deadline_at,
    nl.type,
    nl.status,
    nl.attempt_count,
    nl.max_attempts,
    nl.next_retry_at,
    nl.last_attempted_at,
    nl.sent_at,
    na.id AS notification_attempt_id,
    na.attempt_number,
    na.status AS attempt_status,
    na.provider,
    na.provider_message_id,
    na.failure_reason,
    na.attempted_at
FROM notification_logs nl
         JOIN users u ON u.id = nl.user_id
         JOIN jobs j ON j.id = nl.job_id
         LEFT JOIN notification_attempts na ON na.notification_log_id = nl.id
WHERE nl.type = 'DEADLINE_REMINDER'
ORDER BY nl.updated_at DESC, na.attempt_number DESC
LIMIT 30;

-- 5) unique key가 막아야 하는 중복 로그가 없는지 확인
SELECT
    user_id,
    job_id,
    type,
    COUNT(*) AS duplicate_count
FROM notification_logs
GROUP BY user_id, job_id, type
HAVING COUNT(*) > 1;

-- 6) 로그별 attempt number 중복이 없는지 확인
SELECT
    notification_log_id,
    attempt_number,
    COUNT(*) AS duplicate_attempt_count
FROM notification_attempts
GROUP BY notification_log_id, attempt_number
HAVING COUNT(*) > 1;
