-- WANTED deadline null policy check
--
-- 목적:
-- 1. WANTED deadline 누락이 parser miss가 아니라 raw due_time 부재인지 확인한다.
-- 2. deadline_at IS NULL인 OPEN 공고가 자동 만료 대상이 아님을 확인한다.
-- 3. null deadline 공고를 임의 deadline으로 보정하지 않는 정책을 데이터로 검증한다.
--
-- 실행 위치:
-- IntelliJ DB Console
--
-- 기대 해석:
-- - wanted_parser_missed_deadline_count = 0 이면 parser가 놓친 deadline은 없다.
-- - wanted_null_deadline_open_job_count > 0 이어도 정상이다.
-- - expirable_null_deadline_count = 0 이어야 한다.

WITH wanted_deadline_source AS (
    SELECT
        j.id,
        j.external_id,
        j.title,
        j.company_name,
        j.status,
        j.deadline_at,
        JSON_UNQUOTE(JSON_EXTRACT(j.raw_data, '$.job.due_time')) AS raw_due_time
    FROM jobs j
    WHERE j.source = 'WANTED'
),
     wanted_deadline_summary AS (
         SELECT
             COUNT(*) AS wanted_job_count,
             SUM(CASE WHEN deadline_at IS NULL THEN 1 ELSE 0 END) AS missing_deadline_count,
             ROUND(SUM(CASE WHEN deadline_at IS NULL THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2)
                 AS missing_deadline_rate,
             SUM(CASE WHEN raw_due_time IS NULL OR raw_due_time = '' OR raw_due_time = 'null' THEN 1 ELSE 0 END)
                 AS raw_due_time_missing_count,
             ROUND(
                     SUM(CASE WHEN raw_due_time IS NULL OR raw_due_time = '' OR raw_due_time = 'null' THEN 1 ELSE 0 END)
                         * 100.0 / COUNT(*),
                     2
             ) AS raw_due_time_missing_rate,
             SUM(CASE
                     WHEN raw_due_time IS NOT NULL
                         AND raw_due_time <> ''
                         AND raw_due_time <> 'null'
                         AND deadline_at IS NULL
                         THEN 1 ELSE 0
                 END) AS wanted_parser_missed_deadline_count,
             SUM(CASE WHEN status = 'OPEN' AND deadline_at IS NULL THEN 1 ELSE 0 END)
                 AS wanted_null_deadline_open_job_count
         FROM wanted_deadline_source
     )
SELECT
    wanted_job_count,
    missing_deadline_count,
    missing_deadline_rate,
    raw_due_time_missing_count,
    raw_due_time_missing_rate,
    wanted_parser_missed_deadline_count,
    wanted_null_deadline_open_job_count
FROM wanted_deadline_summary;

SELECT
    COUNT(*) AS expirable_null_deadline_count
FROM jobs
WHERE status = 'OPEN'
  AND deadline_at IS NULL
  AND deadline_at < NOW();

SELECT
    id,
    source,
    external_id,
    title,
    company_name,
    role,
    career_level,
    status,
    deadline_at,
    JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.due_time')) AS raw_due_time,
    original_url
FROM jobs
WHERE source = 'WANTED'
  AND status = 'OPEN'
  AND deadline_at IS NULL
ORDER BY id DESC
    LIMIT 20;

SELECT
    id,
    source,
    external_id,
    title,
    company_name,
    role,
    career_level,
    status,
    deadline_at,
    original_url
FROM jobs
WHERE status = 'OPEN'
  AND deadline_at IS NOT NULL
  AND deadline_at < NOW()
ORDER BY deadline_at ASC
    LIMIT 20;
