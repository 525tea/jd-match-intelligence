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
    CASE MOD(n, 4)
        WHEN 0 THEN 'JUMPIT'
        WHEN 1 THEN 'WANTED'
        WHEN 2 THEN 'SEARCH_BASELINE'
        ELSE 'PERFORMANCE_SYNTHETIC'
        END AS source,
    CONCAT('perf-job-', LPAD(n, 7, '0')) AS external_id,
    SHA2(CONCAT('perf-job-', n), 256) AS canonical_fingerprint,
    CASE MOD(n, 8)
        WHEN 0 THEN CONCAT('Backend Engineer ', n)
        WHEN 1 THEN CONCAT('Data Engineer ', n)
        WHEN 2 THEN CONCAT('Frontend Engineer ', n)
        WHEN 3 THEN CONCAT('DevOps Engineer ', n)
        WHEN 4 THEN CONCAT('ML Engineer ', n)
        WHEN 5 THEN CONCAT('Security Platform Engineer ', n)
        WHEN 6 THEN CONCAT('Full Stack Engineer ', n)
        ELSE CONCAT('Platform Engineer ', n)
        END AS title,
    CONCAT('Perf Company ', LPAD(MOD(n, 500), 3, '0')) AS company_name,
    CONCAT(
            'Synthetic performance job ', n,
            '. This row is generated only for k6 and staging performance tests. ',
            'It must not be inserted into the real collected-data database.'
    ) AS description,
    JSON_ARRAY(
            JSON_OBJECT(
                    'type', 'RESPONSIBILITIES',
                    'title', 'Main Responsibilities',
                    'body', CONCAT('Build scalable services and data flows for synthetic performance job ', n, '.')
            ),
            JSON_OBJECT(
                    'type', 'REQUIREMENTS',
                    'title', 'Requirements',
                    'body', 'Java, Spring Boot, MySQL, Redis, Elasticsearch, and observability experience.'
            )
    ) AS description_sections,
    CONCAT('https://example.com/performance/jobs/', n) AS url,
    CONCAT('https://example.com/performance/jobs/', n) AS original_url,
    CASE MOD(n, 8)
        WHEN 0 THEN 'BACKEND'
        WHEN 1 THEN 'DATA_ENGINEER'
        WHEN 2 THEN 'FRONTEND'
        WHEN 3 THEN 'DEVOPS'
        WHEN 4 THEN 'ML_AI'
        WHEN 5 THEN 'SECURITY'
        WHEN 6 THEN 'FULLSTACK'
        ELSE 'DEVOPS'
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
    CASE MOD(n, 5)
        WHEN 0 THEN 'Seoul'
        WHEN 1 THEN 'Gyeonggi'
        WHEN 2 THEN 'Incheon'
        WHEN 3 THEN 'Busan'
        ELSE 'Remote'
        END AS location_region,
    CASE MOD(n, 5)
        WHEN 0 THEN 'Gangnam'
        WHEN 1 THEN 'Pangyo'
        WHEN 2 THEN 'Songdo'
        WHEN 3 THEN 'Haeundae'
        ELSE NULL
        END AS location_city,
    CASE MOD(n, 3)
        WHEN 0 THEN 'ONSITE'
        WHEN 1 THEN 'HYBRID'
        ELSE 'REMOTE'
        END AS remote_type,
    'KRW' AS salary_currency,
    FALSE AS salary_visible,
    DATE_SUB(@seed_started_at, INTERVAL MOD(n, 90) DAY) AS opened_at,
    CASE
        WHEN MOD(n, 10) = 0 THEN NULL
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
    'performance-dataset-v1' AS crawler_version,
    CONCAT('performance/jobs/', LPAD(n, 7, '0'), '.json') AS raw_snapshot_key,
    SHA2(CONCAT('performance-snapshot-', n), 256) AS raw_snapshot_hash,
    1024 + MOD(n, 4096) AS raw_snapshot_size_bytes,
    'LOCAL_FILE' AS raw_snapshot_storage_type,
    @seed_started_at AS raw_snapshot_saved_at
FROM seq
    ON DUPLICATE KEY UPDATE
                         title = VALUES(title),
                         company_name = VALUES(company_name),
                         description = VALUES(description),
                         description_sections = VALUES(description_sections),
                         role = VALUES(role),
                         career_level = VALUES(career_level),
                         location_region = VALUES(location_region),
                         remote_type = VALUES(remote_type),
                         deadline_at = VALUES(deadline_at),
                         status = VALUES(status),
                         updated_at = CURRENT_TIMESTAMP(6);

INSERT IGNORE INTO job_skills (job_id, skill_id, requirement_type)
SELECT j.id, s.id, 'REQUIRED'
FROM jobs j
         JOIN skills s ON s.normalized_name =
                          CASE MOD(j.id, 6)
                              WHEN 0 THEN 'java'
                              WHEN 1 THEN 'spring boot'
                              WHEN 2 THEN 'mysql'
                              WHEN 3 THEN 'elasticsearch'
                              WHEN 4 THEN 'react'
                              ELSE 'python'
                              END
WHERE j.external_id LIKE 'perf-job-%';

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
WHERE j.external_id LIKE 'perf-job-%';

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
WHERE j.external_id LIKE 'perf-job-%';
