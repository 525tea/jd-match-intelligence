-- daily digest mock smoke verification
-- Run this in IntelliJ DB Console after executing performance/notification/daily-digest-mock-smoke.sh.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @source := 'DAILY_DIGEST_SMOKE' COLLATE utf8mb4_unicode_ci;
SET @digest_type := 'DAILY_DIGEST' COLLATE utf8mb4_unicode_ci;
SET @email_pattern := 'daily-digest-smoke-user-%@example.com' COLLATE utf8mb4_unicode_ci;
SET @project_external_pattern := 'daily-digest-smoke-project-%' COLLATE utf8mb4_unicode_ci;
SET @app_now := DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 HOUR);
SET @digest_date := DATE(@app_now);
SET @deduplication_key := CONCAT(@digest_type, ':date:', @digest_date) COLLATE utf8mb4_unicode_ci;

-- 1) fixture 규모
SELECT
    COUNT(DISTINCT u.id) AS smoke_user_count,
    COUNT(DISTINCT up.id) AS smoke_project_count,
    COUNT(DISTINCT upa.id) AS smoke_analysis_count,
    COUNT(DISTINCT ups.id) AS smoke_project_skill_count,
    COUNT(DISTINCT upet.id) AS smoke_project_experience_tag_count,
    COUNT(DISTINCT j.id) AS smoke_job_count,
    COUNT(DISTINCT jsi.id) AS smoke_job_skill_index_count,
    COUNT(DISTINCT uj.id) AS smoke_saved_deadline_count
FROM users u
         LEFT JOIN user_projects up ON up.user_id = u.id
         LEFT JOIN user_project_analysis upa ON upa.user_project_id = up.id
         LEFT JOIN user_project_skills ups ON ups.analysis_id = upa.id
         LEFT JOIN user_project_experience_tags upet ON upet.analysis_id = upa.id
         LEFT JOIN user_jobs uj ON uj.user_id = u.id
         LEFT JOIN jobs saved_jobs ON saved_jobs.id = uj.job_id AND saved_jobs.source = @source
         LEFT JOIN jobs j ON j.source = @source
         LEFT JOIN job_skill_index jsi ON jsi.job_id = j.id
WHERE u.email LIKE @email_pattern;

-- 2) 사용자별 최신 프로젝트 선정 확인
WITH ranked_projects AS (
    SELECT
        u.email,
        up.id AS user_project_id,
        up.external_id,
        up.name,
        ROW_NUMBER() OVER (
            PARTITION BY u.id
            ORDER BY up.updated_at DESC, up.id DESC
        ) AS rn
    FROM users u
             JOIN user_projects up ON up.user_id = u.id
    WHERE u.email LIKE @email_pattern
      AND up.external_id LIKE @project_external_pattern
)
SELECT
    email,
    user_project_id,
    external_id,
    name
FROM ranked_projects
WHERE rn = 1
ORDER BY email;

-- 3) Daily Digest log 상태별 집계
SELECT
    nl.type,
    nl.status,
    nl.deduplication_key,
    COUNT(*) AS notification_log_count,
    SUM(nl.attempt_count) AS total_attempt_count,
    MIN(nl.next_retry_at) AS earliest_next_retry_at,
    MAX(nl.sent_at) AS latest_sent_at
FROM notification_logs nl
         JOIN users u ON u.id = nl.user_id
WHERE u.email LIKE @email_pattern
  AND nl.type = @digest_type
GROUP BY nl.type, nl.status, nl.deduplication_key
ORDER BY nl.type, nl.status, nl.deduplication_key;

-- 4) Daily Digest attempt 상태별 집계
SELECT
    na.provider,
    na.status,
    COUNT(*) AS notification_attempt_count,
    MIN(na.attempted_at) AS first_attempted_at,
    MAX(na.attempted_at) AS last_attempted_at
FROM notification_attempts na
         JOIN notification_logs nl ON nl.id = na.notification_log_id
         JOIN users u ON u.id = nl.user_id
WHERE u.email LIKE @email_pattern
  AND nl.type = @digest_type
GROUP BY na.provider, na.status
ORDER BY na.provider, na.status;

-- 5) 오늘 이미 Digest가 발송/기록되어 남은 신규 발송 후보가 없는지 확인. 기대값: 0
SELECT
    COUNT(*) AS remaining_daily_digest_candidate_count
FROM users u
WHERE u.email LIKE @email_pattern
  AND NOT EXISTS (
      SELECT 1
      FROM notification_logs nl
      WHERE nl.user_id = u.id
        AND nl.type = @digest_type
        AND nl.deduplication_key = @deduplication_key
  );

-- 6) duplicate notification log count. 기대값: 0 rows.
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

-- 7) duplicate attempt count. 기대값: 0 rows.
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

-- 8) Daily Digest smoke summary
SELECT
    fixture.smoke_user_count,
    fixture.smoke_project_count,
    fixture.smoke_job_count,
    COALESCE(logs.notification_log_count, 0) AS notification_log_count,
    COALESCE(attempts.notification_attempt_count, 0) AS notification_attempt_count,
    COALESCE(sent_logs.sent_log_count, 0) AS sent_log_count,
    COALESCE(failed_attempts.failed_attempt_count, 0) AS failed_attempt_count,
    COALESCE(remaining.remaining_daily_digest_candidate_count, 0) AS remaining_daily_digest_candidate_count
FROM (
    SELECT
        COUNT(DISTINCT u.id) AS smoke_user_count,
        COUNT(DISTINCT up.id) AS smoke_project_count,
        COUNT(DISTINCT j.id) AS smoke_job_count
    FROM users u
             LEFT JOIN user_projects up ON up.user_id = u.id
             LEFT JOIN jobs j ON j.source = @source
    WHERE u.email LIKE @email_pattern
) fixture
         CROSS JOIN (
    SELECT COUNT(*) AS notification_log_count
    FROM notification_logs nl
             JOIN users u ON u.id = nl.user_id
    WHERE u.email LIKE @email_pattern
      AND nl.type = @digest_type
) logs
         CROSS JOIN (
    SELECT COUNT(*) AS notification_attempt_count
    FROM notification_attempts na
             JOIN notification_logs nl ON nl.id = na.notification_log_id
             JOIN users u ON u.id = nl.user_id
    WHERE u.email LIKE @email_pattern
      AND nl.type = @digest_type
) attempts
         CROSS JOIN (
    SELECT COUNT(*) AS sent_log_count
    FROM notification_logs nl
             JOIN users u ON u.id = nl.user_id
    WHERE u.email LIKE @email_pattern
      AND nl.type = @digest_type
      AND nl.status = 'SENT'
) sent_logs
         CROSS JOIN (
    SELECT COUNT(*) AS failed_attempt_count
    FROM notification_attempts na
             JOIN notification_logs nl ON nl.id = na.notification_log_id
             JOIN users u ON u.id = nl.user_id
    WHERE u.email LIKE @email_pattern
      AND nl.type = @digest_type
      AND na.status = 'FAILED'
) failed_attempts
         CROSS JOIN (
    SELECT COUNT(*) AS remaining_daily_digest_candidate_count
    FROM users u
    WHERE u.email LIKE @email_pattern
      AND NOT EXISTS (
          SELECT 1
          FROM notification_logs nl
          WHERE nl.user_id = u.id
            AND nl.type = @digest_type
            AND nl.deduplication_key = @deduplication_key
      )
) remaining;
