-- deadline reminder mock load fixture
-- Run this in IntelliJ DB Console before executing performance/notification/deadline-reminder-mock-load-smoke.sh.
--
-- This fixture intentionally uses MOCK_LOAD source and user@example.com style addresses.
-- It does not require a real Mailgun authorized recipient because the smoke uses MockEmailSender.

SET @user_count = 20;
SET @jobs_per_user = 25;
SET @source = 'NOTIFICATION_MOCK_LOAD' COLLATE utf8mb4_unicode_ci;
SET @email_domain = 'example.com' COLLATE utf8mb4_unicode_ci;
SET @mock_user_email_pattern = 'deadline-mock-user-%@example.com' COLLATE utf8mb4_unicode_ci;
SET @run_suffix = DATE_FORMAT(UTC_TIMESTAMP(6), '%Y%m%d%H%i%s%f');
SET @app_now = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 HOUR);
SET @deadline_at = DATE_ADD(@app_now, INTERVAL 6 HOUR);

-- Clean previous mock-load notification data first.
DELETE na
FROM notification_attempts na
         JOIN notification_logs nl ON nl.id = na.notification_log_id
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source;

DELETE nl
FROM notification_logs nl
         JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source;

DELETE uj
FROM user_jobs uj
         JOIN jobs j ON j.id = uj.job_id
WHERE j.source = @source;

DELETE FROM jobs
WHERE source = @source;

DELETE FROM users
WHERE email LIKE @mock_user_email_pattern;

DROP TEMPORARY TABLE IF EXISTS mock_load_numbers;
CREATE TEMPORARY TABLE mock_load_numbers (
    n INT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO mock_load_numbers (n)
SELECT
    ones.n
    + tens.n * 10
    + hundreds.n * 100
    + 1 AS n
FROM (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) ones
         CROSS JOIN (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) tens
         CROSS JOIN (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) hundreds
WHERE ones.n
          + tens.n * 10
          + hundreds.n * 100
          + 1 <= 1000;

INSERT INTO users (
    email,
    password_hash,
    name,
    role,
    auth_provider,
    provider_id
)
SELECT
    CONCAT('deadline-mock-user-', n, '@', @email_domain),
    'encoded-password',
    CONCAT('Deadline Mock User ', n),
    'USER',
    'LOCAL',
    NULL
FROM mock_load_numbers
WHERE n <= @user_count;

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
SELECT
    @source,
    CONCAT('deadline-reminder-mock-load-', @run_suffix, '-', n),
    CONCAT('마감 알림 Mock Load 백엔드 개발자 ', n),
    'Example Company',
    'Deadline reminder mock load fixture job.',
    CONCAT('https://example.com/jobs/deadline-reminder-mock-load-', @run_suffix, '-', n),
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
FROM mock_load_numbers
WHERE n <= (@user_count * @jobs_per_user);

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
SELECT
    u.id,
    j.id,
    'SAVED',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    NULL,
    NOW(6),
    NOW(6)
FROM users u
         JOIN jobs j
              ON j.source = @source
WHERE u.email LIKE @mock_user_email_pattern;

SELECT
    @source AS source,
    @user_count AS configured_user_count,
    @jobs_per_user AS configured_jobs_per_user,
    COUNT(DISTINCT u.id) AS inserted_user_count,
    COUNT(DISTINCT j.id) AS inserted_job_count,
    COUNT(uj.id) AS inserted_saved_user_job_count,
    @app_now AS app_now,
    @deadline_at AS deadline_at,
    TIMESTAMPDIFF(MINUTE, @app_now, @deadline_at) AS app_minutes_until_deadline
FROM users u
         JOIN user_jobs uj ON uj.user_id = u.id
         JOIN jobs j ON j.id = uj.job_id
WHERE u.email LIKE @mock_user_email_pattern
  AND j.source = @source;
