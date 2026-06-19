SET @source = CONVERT('CANONICAL_SMOKE' USING utf8mb4) COLLATE utf8mb4_unicode_ci;
SET @fingerprint = CONVERT('canonical-smoke|backend-engineer|seoul' USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DELETE FROM job_experience_tags
WHERE job_id IN (
    SELECT id FROM jobs WHERE source = @source
);

DELETE FROM job_skills
WHERE job_id IN (
    SELECT id FROM jobs WHERE source = @source
);

DELETE FROM jobs
WHERE source = @source;

INSERT INTO jobs (
    source,
    external_id,
    canonical_fingerprint,
    title,
    company_name,
    description,
    url,
    original_url,
    role,
    role_detail,
    career_level,
    employment_type,
    location_country,
    location_region,
    location_city,
    remote_type,
    salary_currency,
    salary_visible,
    deadline_at,
    status
)
VALUES
    (
        @source,
        'wanted-1001',
        @fingerprint,
        'Backend Engineer',
        'Example Company',
        'Spring Boot 기반 백엔드 API 개발자를 찾습니다.',
        'https://www.wanted.co.kr/wd/wanted-1001',
        NULL,
        'BACKEND',
        'Java/Spring',
        'MID',
        'FULL_TIME',
        'KR',
        'Seoul',
        'Gangnam',
        'ONSITE',
        'KRW',
        false,
        TIMESTAMP '2026-07-31 23:59:00',
        'OPEN'
    ),
    (
        @source,
        'company-1001',
        @fingerprint,
        'Backend Engineer',
        'Example Company',
        'Spring Boot 기반 백엔드 API 개발자를 찾습니다.',
        'https://jumpit.saramin.co.kr/position/company-1001',
        'https://company.example.com/jobs/backend-engineer',
        'BACKEND',
        'Java/Spring',
        'MID',
        'FULL_TIME',
        'KR',
        'Seoul',
        'Gangnam',
        'ONSITE',
        'KRW',
        false,
        TIMESTAMP '2026-07-31 23:59:00',
        'OPEN'
    );

SELECT
    source,
    canonical_fingerprint,
    COUNT(*) AS canonical_smoke_job_count,
    SUM(CASE WHEN original_url IS NOT NULL AND original_url <> '' THEN 1 ELSE 0 END) AS company_original_url_count
FROM jobs
WHERE source = @source
GROUP BY source, canonical_fingerprint;
