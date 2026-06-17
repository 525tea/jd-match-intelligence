-- daily digest mock smoke fixture
-- Run this in IntelliJ DB Console before executing performance/notification/daily-digest-mock-smoke.sh.
--
-- This fixture intentionally uses example.com users and DAILY_DIGEST_SMOKE source.
-- It does not send real email because the smoke uses MockEmailSender.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @source := 'DAILY_DIGEST_SMOKE' COLLATE utf8mb4_unicode_ci;
SET @digest_type := 'DAILY_DIGEST' COLLATE utf8mb4_unicode_ci;
SET @email_pattern := 'daily-digest-smoke-user-%@example.com' COLLATE utf8mb4_unicode_ci;
SET @project_external_pattern := 'daily-digest-smoke-project-%' COLLATE utf8mb4_unicode_ci;
SET @run_suffix := DATE_FORMAT(UTC_TIMESTAMP(6), '%Y%m%d%H%i%s%f');
SET @app_now := DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 HOUR);
SET @digest_date := DATE(@app_now);

-- Clean previous smoke notification data first.
DELETE na
FROM notification_attempts na
         JOIN notification_logs nl ON nl.id = na.notification_log_id
         JOIN users u ON u.id = nl.user_id
WHERE u.email LIKE @email_pattern;

DELETE nl
FROM notification_logs nl
         JOIN users u ON u.id = nl.user_id
WHERE u.email LIKE @email_pattern;

DELETE uj
FROM user_jobs uj
         JOIN users u ON u.id = uj.user_id
WHERE u.email LIKE @email_pattern;

DELETE jet
FROM job_experience_tags jet
         JOIN jobs j ON j.id = jet.job_id
WHERE j.source = @source;

DELETE jsi
FROM job_skill_index jsi
         JOIN jobs j ON j.id = jsi.job_id
WHERE j.source = @source;

DELETE js
FROM job_skills js
         JOIN jobs j ON j.id = js.job_id
WHERE j.source = @source;

DELETE FROM jobs
WHERE source = @source;

DELETE upet
FROM user_project_experience_tags upet
         JOIN user_project_analysis upa ON upa.id = upet.analysis_id
         JOIN user_projects up ON up.id = upa.user_project_id
WHERE up.external_id LIKE @project_external_pattern;

DELETE ups
FROM user_project_skills ups
         JOIN user_project_analysis upa ON upa.id = ups.analysis_id
         JOIN user_projects up ON up.id = upa.user_project_id
WHERE up.external_id LIKE @project_external_pattern;

DELETE upa
FROM user_project_analysis upa
         JOIN user_projects up ON up.id = upa.user_project_id
WHERE up.external_id LIKE @project_external_pattern;

DELETE FROM user_projects
WHERE external_id LIKE @project_external_pattern;

DELETE FROM users
WHERE email LIKE @email_pattern;

-- Smoke users.
INSERT INTO users (
    email,
    password_hash,
    name,
    role,
    auth_provider,
    provider_id,
    created_at,
    updated_at
)
VALUES
    ('daily-digest-smoke-user-1@example.com', 'encoded-password', 'Daily Digest Smoke User 1', 'USER', 'LOCAL', NULL, NOW(6), NOW(6)),
    ('daily-digest-smoke-user-2@example.com', 'encoded-password', 'Daily Digest Smoke User 2', 'USER', 'LOCAL', NULL, NOW(6), NOW(6));

-- Smoke projects. User 1 intentionally has two projects; runner should use the latest one.
INSERT INTO user_projects (
    user_id,
    source_type,
    external_id,
    name,
    repository_url,
    description,
    created_at,
    updated_at
)
SELECT
    u.id,
    'GITHUB',
    CONCAT('daily-digest-smoke-project-latest-', @run_suffix),
    'Daily Digest Smoke Latest Project',
    'https://example.com/example-org/latest-project',
    'Daily digest latest project fixture.',
    NOW(6),
    DATE_ADD(NOW(6), INTERVAL 2 SECOND)
FROM users u
WHERE u.email = 'daily-digest-smoke-user-1@example.com';

INSERT INTO user_projects (
    user_id,
    source_type,
    external_id,
    name,
    repository_url,
    description,
    created_at,
    updated_at
)
SELECT
    u.id,
    'GITHUB',
    CONCAT('daily-digest-smoke-project-older-', @run_suffix),
    'Daily Digest Smoke Older Project',
    'https://example.com/example-org/older-project',
    'Daily digest older project fixture.',
    NOW(6),
    NOW(6)
FROM users u
WHERE u.email = 'daily-digest-smoke-user-1@example.com';

INSERT INTO user_projects (
    user_id,
    source_type,
    external_id,
    name,
    repository_url,
    description,
    created_at,
    updated_at
)
SELECT
    u.id,
    'GITHUB',
    CONCAT('daily-digest-smoke-project-user-2-', @run_suffix),
    'Daily Digest Smoke User 2 Project',
    'https://example.com/example-org/user-2-project',
    'Daily digest user 2 project fixture.',
    NOW(6),
    DATE_ADD(NOW(6), INTERVAL 1 SECOND)
FROM users u
WHERE u.email = 'daily-digest-smoke-user-2@example.com';

-- Latest analysis per project.
INSERT INTO user_project_analysis (
    user_project_id,
    analysis_version,
    source_hash,
    commit_sha,
    model_version,
    raw_analysis,
    confidence_score,
    analyzed_at
)
SELECT
    up.id,
    1,
    SHA2(CONCAT(up.external_id, ':', @run_suffix), 256),
    NULL,
    'daily-digest-smoke-v1',
    JSON_OBJECT('fixture', 'daily-digest-smoke'),
    0.9500,
    @app_now
FROM user_projects up
WHERE up.external_id LIKE @project_external_pattern;

-- Project skills.
INSERT INTO user_project_skills (
    analysis_id,
    skill_id,
    confidence,
    evidence,
    source
)
SELECT
    upa.id,
    s.id,
    0.9500,
    CONCAT('daily digest smoke fixture: ', s.name),
    'STATIC'
FROM user_project_analysis upa
         JOIN user_projects up ON up.id = upa.user_project_id
         JOIN skills s ON s.name IN ('Java', 'Spring Boot', 'MySQL', 'AWS', 'Docker', 'Git')
WHERE up.external_id LIKE @project_external_pattern;

-- Project experience tags.
INSERT INTO user_project_experience_tags (
    analysis_id,
    tag_code,
    confidence,
    evidence
)
SELECT
    upa.id,
    etc.code,
    0.9000,
    CONCAT('daily digest smoke fixture: ', etc.code)
FROM user_project_analysis upa
         JOIN user_projects up ON up.id = upa.user_project_id
         JOIN experience_tag_codes etc ON etc.code IN ('CI_CD', 'CLOUD_INFRA', 'TESTING')
WHERE up.external_id LIKE @project_external_pattern;

-- Smoke jobs.
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
    status,
    created_at,
    updated_at
)
VALUES
    (
        @source,
        CONCAT('daily-digest-recommendation-', @run_suffix),
        'Daily Digest 추천 백엔드 개발자',
        'Example Company',
        'Java Spring Boot MySQL AWS Docker Git 기반 백엔드 개발자 채용',
        CONCAT('https://example.com/jobs/daily-digest-recommendation-', @run_suffix),
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
        DATE_SUB(@app_now, INTERVAL 1 DAY),
        DATE_ADD(@app_now, INTERVAL 7 DAY),
        'OPEN',
        DATE_SUB(@app_now, INTERVAL 1 DAY),
        @app_now
    ),
    (
        @source,
        CONCAT('daily-digest-jd-match-', @run_suffix),
        'Daily Digest JD 매칭 백엔드 개발자',
        'Example Company',
        'Java Spring Boot 필수, AWS Docker Git 우대, CI/CD 클라우드 인프라 경험',
        CONCAT('https://example.com/jobs/daily-digest-jd-match-', @run_suffix),
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
        DATE_SUB(@app_now, INTERVAL 1 DAY),
        DATE_ADD(@app_now, INTERVAL 8 DAY),
        'OPEN',
        DATE_SUB(@app_now, INTERVAL 1 DAY),
        @app_now
    ),
    (
        @source,
        CONCAT('daily-digest-new-job-', @run_suffix),
        'Daily Digest 신규 수집 백엔드 개발자',
        'Example Company',
        '오늘 새로 수집된 백엔드 개발자 공고',
        CONCAT('https://example.com/jobs/daily-digest-new-job-', @run_suffix),
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
        @app_now,
        DATE_ADD(@app_now, INTERVAL 10 DAY),
        'OPEN',
        DATE_SUB(@app_now, INTERVAL 1 HOUR),
        @app_now
    ),
    (
        @source,
        CONCAT('daily-digest-deadline-', @run_suffix),
        'Daily Digest 마감 임박 저장 공고',
        'Example Company',
        '저장한 마감 임박 백엔드 개발자 공고',
        CONCAT('https://example.com/jobs/daily-digest-deadline-', @run_suffix),
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
        DATE_SUB(@app_now, INTERVAL 1 DAY),
        DATE_ADD(@app_now, INTERVAL 6 HOUR),
        'OPEN',
        DATE_SUB(@app_now, INTERVAL 1 DAY),
        @app_now
    );

-- Job skills.
INSERT INTO job_skills (
    job_id,
    skill_id,
    requirement_type
)
SELECT
    j.id,
    s.id,
    CASE
        WHEN s.name IN ('AWS', 'Docker', 'Git') THEN 'PREFERRED'
        ELSE 'REQUIRED'
        END AS requirement_type
FROM jobs j
         JOIN skills s ON s.name IN ('Java', 'Spring Boot', 'MySQL', 'AWS', 'Docker', 'Git')
WHERE j.source = @source;

-- Job skill index for recommendation/JD matching.
INSERT INTO job_skill_index (
    job_id,
    skill_id,
    requirement_type,
    source,
    job_status,
    role,
    career_level,
    location_region,
    location_city,
    remote_type,
    deadline_at,
    computed_at
)
SELECT
    j.id,
    js.skill_id,
    js.requirement_type,
    j.source,
    j.status,
    j.role,
    j.career_level,
    j.location_region,
    j.location_city,
    j.remote_type,
    j.deadline_at,
    @app_now
FROM jobs j
         JOIN job_skills js ON js.job_id = j.id
WHERE j.source = @source;

-- Job experience tags.
INSERT INTO job_experience_tags (
    job_id,
    tag_code,
    source_phrase
)
SELECT
    j.id,
    etc.code,
    CONCAT('daily digest smoke fixture: ', etc.code)
FROM jobs j
         JOIN experience_tag_codes etc ON etc.code IN ('CI_CD', 'CLOUD_INFRA', 'TESTING')
WHERE j.source = @source;

-- Saved deadline job for all smoke users.
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
    @app_now,
    @app_now,
    NULL,
    NOW(6),
    NOW(6)
FROM users u
         JOIN jobs j ON j.source = @source
WHERE u.email LIKE @email_pattern
  AND j.external_id LIKE CONCAT('daily-digest-deadline-', @run_suffix);

SELECT
    @source AS source,
    @digest_date AS digest_date,
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
