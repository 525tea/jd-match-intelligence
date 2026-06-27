SET SESSION cte_max_recursion_depth = 1000000;
SET @target_job_count = {{PERF_JOB_COUNT}};
SET @seed_started_at = NOW(6);

INSERT INTO skills (name, normalized_name, category)
VALUES
    ('Java', 'java', 'LANGUAGE'),
    ('Spring Boot', 'spring boot', 'FRAMEWORK'),
    ('MySQL', 'mysql', 'DATABASE'),
    ('Redis', 'redis', 'DATABASE'),
    ('Elasticsearch', 'elasticsearch', 'TOOL'),
    ('Kafka', 'kafka', 'TOOL'),
    ('React', 'react', 'FRAMEWORK'),
    ('TypeScript', 'typescript', 'LANGUAGE'),
    ('Python', 'python', 'LANGUAGE'),
    ('Docker', 'docker', 'TOOL'),
    ('Kubernetes', 'kubernetes', 'TOOL')
    ON DUPLICATE KEY UPDATE
                         name = VALUES(name),
                         category = VALUES(category);

INSERT INTO experience_tag_codes (code, name, description)
VALUES
    ('CI_CD', 'CI/CD', 'Continuous integration and delivery experience'),
    ('CLOUD_INFRA', 'Cloud/Infra', 'Cloud and infrastructure operation experience'),
    ('MONITORING', 'Monitoring', 'Monitoring and observability experience'),
    ('PERFORMANCE_OPTIMIZATION', 'Performance Optimization', 'Performance tuning experience'),
    ('TEST_AUTOMATION', 'Test Automation', 'Automated testing experience'),
    ('DATA_PIPELINE', 'Data Pipeline', 'Data pipeline design and operation experience')
    ON DUPLICATE KEY UPDATE
                         name = VALUES(name),
                         description = VALUES(description);

-- role 분포: BACKEND 25%, FRONTEND 15%, DEVOPS 10%, DATA_ENGINEER 10%, 나머지 40%
-- MOD(n, 20) 기준: 0-4 BACKEND, 5-6-7 FRONTEND, 8-9 DEVOPS, 10-11 DATA_ENGINEER, 12 ML_ENGINEER,
--                  13 FULLSTACK, 14 SECURITY, 15 SRE, 16 DBA, 17-18-19 균등 배분
-- location 분포: MOD(n, 20) 0-13 Seoul, 14-16 Gyeonggi(판교), 17-19 기타
INSERT INTO jobs (
    source,
    external_id,
    canonical_fingerprint,
    title,
    company_name,
    description,
    description_sections,
    url,
    original_url,
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
    salary_currency,
    salary_visible,
    opened_at,
    deadline_at,
    status,
    collected_at,
    last_seen_at,
    source_updated_at,
    raw_data,
    crawler_version,
    raw_snapshot_key,
    raw_snapshot_hash,
    raw_snapshot_size_bytes,
    raw_snapshot_storage_type,
    raw_snapshot_saved_at
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < @target_job_count
)
SELECT
    'perf_fixture' AS source,
    CONCAT('perf-job-', LPAD(n, 7, '0')) AS external_id,
    SHA2(CONCAT('perf-fixture-', n), 256) AS canonical_fingerprint,
    CASE MOD(n, 20)
        WHEN 0  THEN CONCAT('백엔드 엔지니어 ', n)
        WHEN 1  THEN CONCAT('Java 백엔드 개발자 ', n)
        WHEN 2  THEN CONCAT('서버 개발자 (Spring Boot) ', n)
        WHEN 3  THEN CONCAT('Backend Software Engineer ', n)
        WHEN 4  THEN CONCAT('백엔드 플랫폼 엔지니어 ', n)
        WHEN 5  THEN CONCAT('프론트엔드 엔지니어 ', n)
        WHEN 6  THEN CONCAT('React 개발자 ', n)
        WHEN 7  THEN CONCAT('Frontend Engineer (TypeScript) ', n)
        WHEN 8  THEN CONCAT('DevOps 엔지니어 ', n)
        WHEN 9  THEN CONCAT('인프라 엔지니어 (Kubernetes) ', n)
        WHEN 10 THEN CONCAT('데이터 엔지니어 ', n)
        WHEN 11 THEN CONCAT('Data Engineer (Python/Spark) ', n)
        WHEN 12 THEN CONCAT('ML Engineer ', n)
        WHEN 13 THEN CONCAT('풀스택 개발자 ', n)
        WHEN 14 THEN CONCAT('보안 엔지니어 ', n)
        WHEN 15 THEN CONCAT('SRE (Site Reliability Engineer) ', n)
        WHEN 16 THEN CONCAT('DBA (MySQL/PostgreSQL) ', n)
        WHEN 17 THEN CONCAT('QA 엔지니어 ', n)
        WHEN 18 THEN CONCAT('안드로이드 개발자 ', n)
        ELSE        CONCAT('iOS 개발자 ', n)
        END AS title,
    CONCAT('Perf Company ', LPAD(MOD(n, 500), 3, '0')) AS company_name,
    CASE MOD(n, 20)
        WHEN 0  THEN CONCAT('백엔드 엔지니어를 채용합니다. Java Spring Boot MySQL Redis 경험자 우대. REST API 설계, JPA, Hibernate. 대용량 트래픽 처리 경험 필수. Elasticsearch Kafka 우대. MSA 경험 환영. ', n)
        WHEN 1  THEN CONCAT('Java 백엔드 개발자를 채용합니다. Spring Boot JPA MySQL 기반 서비스 개발. Redis 캐싱, Kafka 비동기 처리. AWS 클라우드 경험 우대. Docker Kubernetes CI/CD. ', n)
        WHEN 2  THEN CONCAT('서버 개발자를 모집합니다. Spring Boot Spring Security OAuth2 JWT 인증. MySQL 쿼리 최적화. Redis Lettuce 연동. 테스트 코드 JUnit Mockito 필수. ', n)
        WHEN 3  THEN CONCAT('Backend Software Engineer 채용. Java Kotlin Spring Framework. MySQL PostgreSQL. RESTful API gRPC. Docker Kubernetes AWS EKS. Prometheus Grafana 모니터링. ', n)
        WHEN 4  THEN CONCAT('백엔드 플랫폼 엔지니어 채용. Java Spring Boot Kafka Elasticsearch. 대규모 데이터 처리. HikariCP 커넥션 풀 관리. Redis 분산 캐시. 성능 최적화 경험 필수. ', n)
        WHEN 5  THEN CONCAT('프론트엔드 엔지니어를 채용합니다. React TypeScript Next.js 필수. Redux Recoil 상태관리. Webpack Vite 빌드 도구. 반응형 UI 개발 경험. REST API 연동. ', n)
        WHEN 6  THEN CONCAT('React 개발자 모집. TypeScript React Hooks Context API. Storybook 컴포넌트 문서화. Jest Testing Library 테스트. CSS-in-JS styled-components tailwind. ', n)
        WHEN 7  THEN CONCAT('Frontend Engineer 채용. TypeScript React Next.js GraphQL. Zustand 상태관리. Playwright E2E 테스트. Figma 협업 경험. SSR SSG 최적화 경험 우대. ', n)
        WHEN 8  THEN CONCAT('DevOps 엔지니어 채용. Kubernetes Docker AWS Terraform. CI/CD Jenkins GitHub Actions. Prometheus Grafana Loki 모니터링. ArgoCD GitOps. 인프라 비용 최적화. ', n)
        WHEN 9  THEN CONCAT('인프라 엔지니어 채용. Kubernetes EKS ECS AWS. Terraform Helm. Prometheus Grafana Alertmanager. Istio 서비스 메시. 네트워크 설계 로드밸런싱 경험. ', n)
        WHEN 10 THEN CONCAT('데이터 엔지니어를 채용합니다. Python Spark Kafka Airflow. 데이터 파이프라인 ETL 설계. BigQuery Redshift Snowflake. 실시간 스트리밍 처리. dbt 데이터 모델링. ', n)
        WHEN 11 THEN CONCAT('Data Engineer 채용. Python PySpark Kafka Flink. Airflow 워크플로우 오케스트레이션. Hive Presto Trino. AWS Glue EMR S3. 데이터 레이크 레이크하우스 경험. ', n)
        WHEN 12 THEN CONCAT('ML Engineer 채용. Python TensorFlow PyTorch. MLOps Kubeflow MLflow. 모델 서빙 FastAPI Triton. 피처 스토어 설계. A/B 테스트 실험 플랫폼. GPU 학습 파이프라인. ', n)
        WHEN 13 THEN CONCAT('풀스택 개발자 채용. Java Spring Boot 백엔드, React TypeScript 프론트엔드. MySQL Redis. Docker Kubernetes AWS. 소규모 팀에서 풀스택으로 제품 개발. ', n)
        WHEN 14 THEN CONCAT('보안 엔지니어 채용. 취약점 분석 모의해킹 SIEM. Elasticsearch Kibana 보안 로그 분석. WAF IDS IPS 운영. OWASP Top 10. 클라우드 보안 AWS Security Hub. ', n)
        WHEN 15 THEN CONCAT('SRE 채용. Kubernetes AWS GCP. Prometheus Grafana Datadog. SLO SLI 에러 버짓. 인시던트 대응 포스트모텀. Terraform IaC. 카오스 엔지니어링. ', n)
        WHEN 16 THEN CONCAT('DBA 채용. MySQL PostgreSQL 운영 최적화. 쿼리 튜닝 인덱스 설계. 고가용성 HA 레플리카. 백업 복구 정책. AWS RDS Aurora. 마이그레이션 경험 필수. ', n)
        WHEN 17 THEN CONCAT('QA 엔지니어 채용. Selenium Playwright 자동화. JMeter k6 성능 테스트. API 테스트 Postman RestAssured. CI/CD 통합. 테스트 전략 설계 경험. ', n)
        WHEN 18 THEN CONCAT('안드로이드 개발자 채용. Kotlin Jetpack Compose. MVVM Hilt Coroutines Flow. Retrofit OkHttp. Room SQLite. Firebase. Play Store 출시 경험. ', n)
        ELSE        CONCAT('iOS 개발자 채용. Swift SwiftUI UIKit. MVVM Combine. URLSession Alamofire. CoreData Realm. App Store 출시 경험. Xcode 테스트 XCTest. ', n)
        END AS description,
    JSON_ARRAY(
        JSON_OBJECT(
            'type', 'RESPONSIBILITIES',
            'title', '주요 업무',
            'body', CASE MOD(n, 20)
                WHEN 0  THEN 'Java Spring Boot 기반 백엔드 API 개발 및 운영'
                WHEN 1  THEN 'Java 기반 서비스 서버 개발 및 코드 리뷰'
                WHEN 2  THEN 'Spring Boot 기반 웹 서비스 개발 및 배포'
                WHEN 3  THEN 'Backend API 설계 및 구현 (Java/Kotlin)'
                WHEN 4  THEN '플랫폼 백엔드 시스템 설계 및 개발'
                WHEN 5  THEN 'React 기반 사용자 인터페이스 개발'
                WHEN 6  THEN 'React 컴포넌트 설계 및 구현'
                WHEN 7  THEN 'TypeScript React 프론트엔드 개발'
                WHEN 8  THEN 'Kubernetes 기반 인프라 운영 및 자동화'
                WHEN 9  THEN 'AWS 인프라 설계 및 Kubernetes 운영'
                WHEN 10 THEN 'Python 기반 데이터 파이프라인 설계 및 구축'
                WHEN 11 THEN 'Spark Kafka 기반 데이터 처리 파이프라인 개발'
                WHEN 12 THEN '머신러닝 모델 학습 파이프라인 및 서빙 시스템 구축'
                WHEN 13 THEN '풀스택 웹 애플리케이션 개발 (Spring Boot + React)'
                WHEN 14 THEN '보안 취약점 분석 및 보안 이벤트 모니터링'
                WHEN 15 THEN 'SLO/SLI 설계 및 서비스 안정성 향상'
                WHEN 16 THEN 'MySQL PostgreSQL 운영 및 쿼리 최적화'
                WHEN 17 THEN '자동화 테스트 설계 및 CI/CD 통합'
                WHEN 18 THEN 'Kotlin 기반 Android 앱 개발'
                ELSE        'Swift 기반 iOS 앱 개발'
                END
        ),
        JSON_OBJECT(
            'type', 'REQUIREMENTS',
            'title', '자격 요건',
            'body', CASE MOD(n, 20)
                WHEN 0  THEN 'Java Spring Boot 3년 이상, MySQL 실무 경험, RESTful API 설계 능력'
                WHEN 1  THEN 'Java 백엔드 개발 경험, Spring Framework, JPA 이해'
                WHEN 2  THEN 'Spring Boot 실무 경험, MySQL 쿼리 작성 능력, Git 협업'
                WHEN 3  THEN 'Java 또는 Kotlin 백엔드 개발 경험, REST API 설계'
                WHEN 4  THEN 'Java 심화 지식, 대규모 트래픽 처리 경험, 분산 시스템 이해'
                WHEN 5  THEN 'React TypeScript 실무 경험 2년 이상, 컴포넌트 설계 능력'
                WHEN 6  THEN 'React 개발 경험, JavaScript ES6+ 이해, 상태관리 경험'
                WHEN 7  THEN 'TypeScript React 실무 경험, Next.js 이해'
                WHEN 8  THEN 'Kubernetes Docker 운영 경험, AWS 클라우드 지식'
                WHEN 9  THEN 'AWS 인프라 경험, Linux 시스템 관리, Terraform IaC'
                WHEN 10 THEN 'Python 실무 경험, SQL 능숙, ETL 파이프라인 개발 경험'
                WHEN 11 THEN 'PySpark 또는 Spark 사용 경험, 데이터 파이프라인 운영'
                WHEN 12 THEN 'Python ML 라이브러리 경험, 모델 학습 및 평가 경험'
                WHEN 13 THEN 'Java 백엔드 및 React 프론트엔드 개발 경험'
                WHEN 14 THEN '보안 취약점 분석 경험, SIEM 운영 지식'
                WHEN 15 THEN 'Linux 서버 운영 경험, Kubernetes 지식, 모니터링 도구 경험'
                WHEN 16 THEN 'MySQL DBA 또는 개발 경험, 쿼리 최적화 능력'
                WHEN 17 THEN '자동화 테스트 도구 경험, 테스트 케이스 설계 능력'
                WHEN 18 THEN 'Kotlin Android 개발 경험, Jetpack 라이브러리 이해'
                ELSE        'Swift iOS 개발 경험, UIKit 또는 SwiftUI 실무 경험'
                END
        )
    ) AS description_sections,
    CONCAT('https://example.com/perf/jobs/', n) AS url,
    CONCAT('https://example.com/perf/jobs/', n) AS original_url,
    CASE MOD(n, 20)
        WHEN 0  THEN 'BACKEND'
        WHEN 1  THEN 'BACKEND'
        WHEN 2  THEN 'BACKEND'
        WHEN 3  THEN 'BACKEND'
        WHEN 4  THEN 'BACKEND'
        WHEN 5  THEN 'FRONTEND'
        WHEN 6  THEN 'FRONTEND'
        WHEN 7  THEN 'FRONTEND'
        WHEN 8  THEN 'DEVOPS'
        WHEN 9  THEN 'DEVOPS'
        WHEN 10 THEN 'DATA_ENGINEER'
        WHEN 11 THEN 'DATA_ENGINEER'
        WHEN 12 THEN 'ML_ENGINEER'
        WHEN 13 THEN 'FULLSTACK'
        WHEN 14 THEN 'SECURITY'
        WHEN 15 THEN 'SRE'
        WHEN 16 THEN 'DBA'
        WHEN 17 THEN 'QA'
        WHEN 18 THEN 'ANDROID'
        ELSE        'IOS'
        END AS role,
    NULL AS role_detail,
    CASE MOD(n, 5)
        WHEN 0 THEN 'NEWCOMER'
        WHEN 1 THEN 'JUNIOR'
        WHEN 2 THEN 'MID'
        WHEN 3 THEN 'SENIOR'
        ELSE 'ANY'
        END AS career_level,
    CASE MOD(n, 5)
        WHEN 0 THEN NULL
        WHEN 1 THEN 1
        WHEN 2 THEN 3
        WHEN 3 THEN 5
        ELSE NULL
        END AS min_experience_years,
    CASE MOD(n, 5)
        WHEN 0 THEN NULL
        WHEN 1 THEN 3
        WHEN 2 THEN 7
        WHEN 3 THEN 10
        ELSE NULL
        END AS max_experience_years,
    'ANY' AS education_level,
    'FULL_TIME' AS employment_type,
    CASE MOD(n, 4)
        WHEN 0 THEN 'STARTUP'
        WHEN 1 THEN 'SME'
        WHEN 2 THEN 'MID_SIZE'
        ELSE 'ENTERPRISE'
        END AS company_size,
    CASE MOD(n, 4)
        WHEN 0 THEN 'SaaS'
        WHEN 1 THEN 'FinTech'
        WHEN 2 THEN 'Commerce'
        ELSE 'AI Platform'
        END AS industry,
    'KR' AS location_country,
    -- location 분포: 서울 70% (MOD 0-13), 경기/판교 15% (MOD 14-16), 기타 15% (MOD 17-19)
    CASE MOD(n, 20)
        WHEN 0  THEN 'Seoul'
        WHEN 1  THEN 'Seoul'
        WHEN 2  THEN 'Seoul'
        WHEN 3  THEN 'Seoul'
        WHEN 4  THEN 'Seoul'
        WHEN 5  THEN 'Seoul'
        WHEN 6  THEN 'Seoul'
        WHEN 7  THEN 'Seoul'
        WHEN 8  THEN 'Seoul'
        WHEN 9  THEN 'Seoul'
        WHEN 10 THEN 'Seoul'
        WHEN 11 THEN 'Seoul'
        WHEN 12 THEN 'Seoul'
        WHEN 13 THEN 'Seoul'
        WHEN 14 THEN 'Gyeonggi'
        WHEN 15 THEN 'Gyeonggi'
        WHEN 16 THEN 'Gyeonggi'
        WHEN 17 THEN 'Busan'
        WHEN 18 THEN 'Incheon'
        ELSE        'Remote'
        END AS location_region,
    CASE MOD(n, 20)
        WHEN 0  THEN 'Gangnam'
        WHEN 1  THEN 'Mapo'
        WHEN 2  THEN 'Jongno'
        WHEN 3  THEN 'Gangnam'
        WHEN 4  THEN 'Yeongdeungpo'
        WHEN 5  THEN 'Gangnam'
        WHEN 6  THEN 'Mapo'
        WHEN 7  THEN 'Gangnam'
        WHEN 8  THEN 'Gangnam'
        WHEN 9  THEN 'Jongno'
        WHEN 10 THEN 'Gangnam'
        WHEN 11 THEN 'Yeongdeungpo'
        WHEN 12 THEN 'Gangnam'
        WHEN 13 THEN 'Mapo'
        WHEN 14 THEN 'Pangyo'
        WHEN 15 THEN 'Pangyo'
        WHEN 16 THEN 'Suwon'
        WHEN 17 THEN 'Haeundae'
        WHEN 18 THEN 'Songdo'
        ELSE        NULL
        END AS location_city,
    CASE MOD(n, 10)
        WHEN 0 THEN 'REMOTE'
        WHEN 1 THEN 'HYBRID'
        ELSE 'ONSITE'
        END AS remote_type,
    'KRW' AS salary_currency,
    FALSE AS salary_visible,
    DATE_SUB(@seed_started_at, INTERVAL MOD(n, 90) DAY) AS opened_at,
    -- deadline null 비율 약 9% (MOD(n, 11) = 0)
    CASE
        WHEN MOD(n, 11) = 0 THEN NULL
        ELSE DATE_ADD(@seed_started_at, INTERVAL MOD(n, 45) DAY)
        END AS deadline_at,
    CASE
        WHEN MOD(n, 20) = 0 THEN 'CLOSED'
        ELSE 'OPEN'
        END AS status,
    @seed_started_at AS collected_at,
    @seed_started_at AS last_seen_at,
    @seed_started_at AS source_updated_at,
    JSON_OBJECT(
        'kind', 'performance-fixture',
        'sequence', n,
        'generatedAt', CAST(@seed_started_at AS CHAR)
    ) AS raw_data,
    'performance-dataset-v2' AS crawler_version,
    CONCAT('performance/jobs/', LPAD(n, 7, '0'), '.json') AS raw_snapshot_key,
    SHA2(CONCAT('perf-fixture-snapshot-', n), 256) AS raw_snapshot_hash,
    1024 + MOD(n, 4096) AS raw_snapshot_size_bytes,
    'LOCAL_FILE' AS raw_snapshot_storage_type,
    @seed_started_at AS raw_snapshot_saved_at
FROM seq
    ON DUPLICATE KEY UPDATE
                         source = VALUES(source),
                         title = VALUES(title),
                         company_name = VALUES(company_name),
                         description = VALUES(description),
                         description_sections = VALUES(description_sections),
                         role = VALUES(role),
                         career_level = VALUES(career_level),
                         location_region = VALUES(location_region),
                         location_city = VALUES(location_city),
                         remote_type = VALUES(remote_type),
                         deadline_at = VALUES(deadline_at),
                         status = VALUES(status),
                         crawler_version = VALUES(crawler_version),
                         updated_at = CURRENT_TIMESTAMP(6);

INSERT IGNORE INTO job_skills (job_id, skill_id, requirement_type)
SELECT j.id, s.id, 'REQUIRED'
FROM jobs j
         JOIN skills s ON s.normalized_name =
                          CASE MOD(j.id, 11)
                              WHEN 0  THEN 'java'
                              WHEN 1  THEN 'spring boot'
                              WHEN 2  THEN 'mysql'
                              WHEN 3  THEN 'elasticsearch'
                              WHEN 4  THEN 'react'
                              WHEN 5  THEN 'python'
                              WHEN 6  THEN 'typescript'
                              WHEN 7  THEN 'kafka'
                              WHEN 8  THEN 'docker'
                              WHEN 9  THEN 'kubernetes'
                              ELSE 'redis'
                              END
WHERE j.source = 'perf_fixture';

INSERT IGNORE INTO job_skills (job_id, skill_id, requirement_type)
SELECT j.id, s.id, 'PREFERRED'
FROM jobs j
         JOIN skills s ON s.normalized_name =
                          CASE MOD(j.id, 5)
                              WHEN 0 THEN 'redis'
                              WHEN 1 THEN 'kafka'
                              WHEN 2 THEN 'docker'
                              WHEN 3 THEN 'kubernetes'
                              ELSE 'typescript'
                              END
WHERE j.source = 'perf_fixture';

INSERT IGNORE INTO job_experience_tags (job_id, tag_code, source_phrase)
SELECT j.id,
       CASE MOD(j.id, 6)
           WHEN 0 THEN 'CI_CD'
           WHEN 1 THEN 'CLOUD_INFRA'
           WHEN 2 THEN 'MONITORING'
           WHEN 3 THEN 'PERFORMANCE_OPTIMIZATION'
           WHEN 4 THEN 'TEST_AUTOMATION'
           ELSE 'DATA_PIPELINE'
           END AS tag_code,
       'performance fixture distribution'
FROM jobs j
WHERE j.source = 'perf_fixture';
