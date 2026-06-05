DELETE FROM jobs
WHERE source = 'SEARCH_BASELINE';

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
    employment_type,
    location_country,
    location_region,
    location_city,
    remote_type,
    salary_currency,
    salary_visible,
    status
) VALUES
(
    'SEARCH_BASELINE',
    'kubernetes-platform-engineer',
    'Kubernetes 플랫폼 엔지니어',
    'Example Cloud',
    'Kubernetes 기반 컨테이너 플랫폼과 클러스터 운영 자동화를 담당합니다.',
    'https://example.com/jobs/kubernetes-platform-engineer',
    'DEVOPS',
    'Kubernetes Platform',
    'ANY',
    'FULL_TIME',
    'KR',
    'Seoul',
    'Gangnam',
    'HYBRID',
    'KRW',
    false,
    'OPEN'
),
(
    'SEARCH_BASELINE',
    'k8s-platform-engineer',
    'k8s 플랫폼 엔지니어',
    'Example Infra',
    'k8s 기반 배포 자동화와 서비스 운영 환경을 개선합니다.',
    'https://example.com/jobs/k8s-platform-engineer',
    'DEVOPS',
    'k8s Platform',
    'ANY',
    'FULL_TIME',
    'KR',
    'Seoul',
    'Gangnam',
    'HYBRID',
    'KRW',
    false,
    'OPEN'
),
(
    'SEARCH_BASELINE',
    'backend-spring-engineer',
    '백엔드 개발자',
    'Example Backend',
    'Spring Boot 기반 API 서버와 MySQL 데이터 모델을 개발합니다.',
    'https://example.com/jobs/backend-spring-engineer',
    'BACKEND',
    'Java/Spring',
    'ANY',
    'FULL_TIME',
    'KR',
    'Seoul',
    'Mapo',
    'ONSITE',
    'KRW',
    false,
    'OPEN'
);

SELECT
    id,
    source,
    external_id,
    title,
    company_name
FROM jobs
WHERE source = 'SEARCH_BASELINE'
ORDER BY id;
