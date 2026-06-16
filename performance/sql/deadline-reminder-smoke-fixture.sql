-- deadline reminder batch smoke fixture
-- Run this in IntelliJ DB Console before executing performance/notification/deadline-reminder-batch-smoke.sh.
--
-- Important:
-- The Spring app runs with Asia/Seoul local time, while MySQL NOW() can differ by session/server timezone.
-- This fixture stores deadline_at using UTC_TIMESTAMP + 12 hours so the row is safely in the future
-- from the Spring app point of view and still inside the 24h reminder window.
-- Replace @user_email with a Mailgun Authorized Recipient when using a sandbox domain.

SET @user_email = 'user@example.com';
SET @external_id = CONCAT('deadline-reminder-smoke-', DATE_FORMAT(UTC_TIMESTAMP(6), '%Y%m%d%H%i%s%f'));
SET @deadline_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 12 HOUR);

INSERT INTO users (
    email,
    password_hash,
    name,
    role,
    auth_provider,
    provider_id
)
VALUES (
    @user_email,
    'encoded-password',
    'Smoke User',
    'USER',
    'LOCAL',
    NULL
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    updated_at = NOW(6);

SET @user_id = (
    SELECT id
    FROM users
    WHERE email = @user_email
    LIMIT 1
);

INSERT INTO jobs (
    source,
    external_id,
    title,
    company_name,
    description,
    url,
    role,
    role_detail,
    career_level,
    min_experience_years,
    max_experience_years,
    education_level,
    employment_type,
    company_size,
    industry,
    location_country,
    location_region,
    location_city,
    remote_type,
    salary_min,
    salary_max,
    salary_currency,
    salary_visible,
    hiring_count,
    opened_at,
    deadline_at,
    status
)
VALUES (
    'NOTIFICATION_SMOKE',
    @external_id,
    '마감 알림 스모크 백엔드 개발자',
    'Example Company',
    'Deadline reminder smoke test job.',
    CONCAT('https://example.com/jobs/', @external_id),
    'BACKEND',
    'Java/Spring',
    'MID',
    3,
    7,
    'BACHELOR',
    'FULL_TIME',
    'STARTUP',
    'IT',
    'KR',
    'Seoul',
    'Gangnam',
    'ONSITE',
    40000000,
    70000000,
    'KRW',
    TRUE,
    1,
    UTC_TIMESTAMP(6),
    @deadline_at,
    'OPEN'
);

SET @job_id = LAST_INSERT_ID();

INSERT INTO user_jobs (
    user_id,
    job_id,
    status,
    viewed_at,
    saved_at,
    ignored_at,
    created_at,
    updated_at
)
VALUES (
    @user_id,
    @job_id,
    'SAVED',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    NULL,
    NOW(6),
    NOW(6)
);

DELETE na
FROM notification_attempts na
         JOIN notification_logs nl ON nl.id = na.notification_log_id
WHERE nl.user_id = @user_id
  AND nl.job_id = @job_id
  AND nl.type = 'DEADLINE_REMINDER';

DELETE FROM notification_logs
WHERE user_id = @user_id
  AND job_id = @job_id
  AND type = 'DEADLINE_REMINDER';

SET @app_now = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 HOUR);

SELECT
    @user_id AS user_id,
    @user_email AS user_email,
    @job_id AS job_id,
    @external_id AS external_id,
    @app_now AS app_now,
    @deadline_at AS deadline_at,
    TIMESTAMPDIFF(MINUTE, @app_now, @deadline_at) AS app_minutes_until_deadline;
