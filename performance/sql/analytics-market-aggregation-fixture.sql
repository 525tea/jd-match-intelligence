SET @target_month = DATE_FORMAT(CURRENT_DATE(), '%Y-%m-01');
SET @created_at = TIMESTAMP(@target_month, '09:00:00');

DELETE jet
FROM job_experience_tags jet
JOIN jobs j ON j.id = jet.job_id
WHERE j.source = 'ANALYTICS_SMOKE';

DELETE js
FROM job_skills js
JOIN jobs j ON j.id = js.job_id
WHERE j.source = 'ANALYTICS_SMOKE';

DELETE FROM jobs
WHERE source = 'ANALYTICS_SMOKE';

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
    employment_type,
    industry,
    location_country,
    location_region,
    location_city,
    remote_type,
    salary_currency,
    salary_visible,
    opened_at,
    deadline_at,
    status,
    created_at,
    updated_at
)
VALUES
    (
        'ANALYTICS_SMOKE',
        'backend-spring-redis',
        '백엔드 플랫폼 개발자',
        'JobFlow',
        'Spring Boot 기반 API와 Redis 캐시를 운영하며 대용량 트래픽을 처리합니다.',
        'https://example.com/jobs/analytics-smoke-backend-spring-redis',
        'BACKEND',
        'Java Spring Boot Redis',
        'JUNIOR',
        0,
        3,
        'FULL_TIME',
        'IT',
        'KR',
        'Seoul',
        'Gangnam',
        'HYBRID',
        'KRW',
        false,
        @created_at,
        DATE_ADD(@target_month, INTERVAL 1 MONTH),
        'OPEN',
        @created_at,
        @created_at
    ),
    (
        'ANALYTICS_SMOKE',
        'platform-kubernetes-redis',
        '플랫폼 엔지니어',
        'JobFlow',
        'Kubernetes 기반 플랫폼에서 Redis와 Spring 서비스를 안정적으로 운영합니다.',
        'https://example.com/jobs/analytics-smoke-platform-kubernetes-redis',
        'BACKEND',
        'Kubernetes Redis Spring',
        'MID',
        3,
        6,
        'FULL_TIME',
        'IT',
        'KR',
        'Seoul',
        'Seongdong',
        'REMOTE',
        'KRW',
        false,
        @created_at,
        DATE_ADD(@target_month, INTERVAL 1 MONTH),
        'OPEN',
        @created_at,
        @created_at
    ),
    (
        'ANALYTICS_SMOKE',
        'backend-mysql-performance',
        '백엔드 성능 개선 엔지니어',
        'JobFlow',
        'Spring Boot와 MySQL 기반 서비스의 쿼리 튜닝과 성능 최적화를 수행합니다.',
        'https://example.com/jobs/analytics-smoke-backend-mysql-performance',
        'BACKEND',
        'Spring Boot MySQL performance',
        'MID',
        2,
        5,
        'FULL_TIME',
        'IT',
        'KR',
        'Gyeonggi',
        'Pangyo',
        'ONSITE',
        'KRW',
        false,
        @created_at,
        DATE_ADD(@target_month, INTERVAL 1 MONTH),
        'OPEN',
        @created_at,
        @created_at
    );

INSERT INTO job_skills (job_id, skill_id, requirement_type)
SELECT j.id, s.id, 'REQUIRED'
FROM jobs j
         JOIN skills s ON s.normalized_name = 'spring boot'
WHERE j.source = 'ANALYTICS_SMOKE'
  AND j.external_id IN ('backend-spring-redis', 'backend-mysql-performance');

INSERT INTO job_skills (job_id, skill_id, requirement_type)
SELECT j.id, s.id, 'PREFERRED'
FROM jobs j
         JOIN skills s ON s.normalized_name = 'redis'
WHERE j.source = 'ANALYTICS_SMOKE'
  AND j.external_id IN ('backend-spring-redis', 'platform-kubernetes-redis');

INSERT INTO job_skills (job_id, skill_id, requirement_type)
SELECT j.id, s.id, 'REQUIRED'
FROM jobs j
         JOIN skills s ON s.normalized_name = 'kubernetes'
WHERE j.source = 'ANALYTICS_SMOKE'
  AND j.external_id = 'platform-kubernetes-redis';

INSERT INTO job_skills (job_id, skill_id, requirement_type)
SELECT j.id, s.id, 'PREFERRED'
FROM jobs j
         JOIN skills s ON s.normalized_name = 'mysql'
WHERE j.source = 'ANALYTICS_SMOKE'
  AND j.external_id = 'backend-mysql-performance';

INSERT INTO job_experience_tags (job_id, tag_code, source_phrase)
SELECT j.id, 'HIGH_TRAFFIC', '대용량 트래픽'
FROM jobs j
WHERE j.source = 'ANALYTICS_SMOKE'
  AND j.external_id IN ('backend-spring-redis', 'platform-kubernetes-redis');

INSERT INTO job_experience_tags (job_id, tag_code, source_phrase)
SELECT j.id, 'PERFORMANCE', '성능 최적화'
FROM jobs j
WHERE j.source = 'ANALYTICS_SMOKE'
  AND j.external_id = 'backend-mysql-performance';

SELECT
    j.id,
    j.source,
    j.external_id,
    j.title,
    j.created_at
FROM jobs j
WHERE j.source = 'ANALYTICS_SMOKE'
ORDER BY j.external_id;
