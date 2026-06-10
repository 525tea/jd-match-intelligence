SET @min_id = 0;

WITH wanted_deadline AS (
    SELECT
        id,
        external_id,
        title,
        company_name,
        deadline_at,
        original_url,
        JSON_EXTRACT(raw_data, '$.job.due_time') AS due_time_json,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.due_time')), 'null') AS raw_due_time,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.status')), 'null') AS raw_status,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.is_closed')), 'null') AS raw_is_closed,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.is_active')), 'null') AS raw_is_active,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.hidden')), 'null') AS raw_hidden
    FROM jobs
    WHERE source = 'WANTED'
      AND id >= @min_id
)
SELECT
    COUNT(*) AS wanted_job_count,
    SUM(CASE WHEN deadline_at IS NULL THEN 1 ELSE 0 END) AS missing_deadline_count,
    ROUND(SUM(CASE WHEN deadline_at IS NULL THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS missing_deadline_rate,
    SUM(CASE WHEN raw_due_time IS NOT NULL
                  AND raw_due_time <> ''
             THEN 1 ELSE 0 END) AS raw_due_time_present_count,
    SUM(CASE WHEN raw_due_time IS NULL
                  OR raw_due_time = ''
             THEN 1 ELSE 0 END) AS raw_due_time_missing_count
FROM wanted_deadline;

WITH wanted_deadline AS (
    SELECT
        id,
        deadline_at,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.due_time')), 'null') AS raw_due_time
    FROM jobs
    WHERE source = 'WANTED'
      AND id >= @min_id
)
SELECT
    CASE
        WHEN raw_due_time IS NULL
             OR raw_due_time = ''
            THEN 'RAW_DUE_TIME_MISSING'
        WHEN deadline_at IS NULL
            THEN 'PARSER_MISSED_DUE_TIME'
        ELSE 'DEADLINE_PARSED'
        END AS deadline_status,
    COUNT(*) AS job_count
FROM wanted_deadline
GROUP BY deadline_status
ORDER BY FIELD(deadline_status, 'PARSER_MISSED_DUE_TIME', 'RAW_DUE_TIME_MISSING', 'DEADLINE_PARSED');

WITH wanted_deadline AS (
    SELECT
        id,
        external_id,
        title,
        company_name,
        deadline_at,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.due_time')), 'null') AS raw_due_time,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.status')), 'null') AS raw_status,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.is_closed')), 'null') AS raw_is_closed,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.is_active')), 'null') AS raw_is_active,
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.hidden')), 'null') AS raw_hidden,
        original_url
    FROM jobs
    WHERE source = 'WANTED'
      AND id >= @min_id
)
SELECT
    id,
    external_id,
    title,
    company_name,
    deadline_at,
    raw_due_time,
    raw_status,
    raw_is_closed,
    raw_is_active,
    raw_hidden,
    original_url
FROM wanted_deadline
ORDER BY deadline_at IS NULL DESC, id DESC
LIMIT 100;

WITH wanted_deadline AS (
    SELECT
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_data, '$.job.due_time')), 'null') AS raw_due_time
    FROM jobs
    WHERE source = 'WANTED'
      AND id >= @min_id
)
SELECT
    raw_due_time,
    COUNT(*) AS job_count
FROM wanted_deadline
GROUP BY raw_due_time
ORDER BY raw_due_time IS NULL DESC, job_count DESC, raw_due_time ASC;

SELECT
    SUM(CASE WHEN raw_data LIKE '%deadline%' THEN 1 ELSE 0 END) AS contains_deadline_text_count,
    SUM(CASE WHEN raw_data LIKE '%due%' THEN 1 ELSE 0 END) AS contains_due_text_count,
    SUM(CASE WHEN raw_data LIKE '%close%' THEN 1 ELSE 0 END) AS contains_close_text_count,
    SUM(CASE WHEN raw_data LIKE '%end_time%' THEN 1 ELSE 0 END) AS contains_end_time_text_count,
    SUM(CASE WHEN raw_data LIKE '%until%' THEN 1 ELSE 0 END) AS contains_until_text_count,
    SUM(CASE WHEN raw_data LIKE '%period%' THEN 1 ELSE 0 END) AS contains_period_text_count
FROM jobs
WHERE source = 'WANTED'
  AND id >= @min_id;

SELECT
    id,
    external_id,
    title,
    JSON_KEYS(raw_data, '$.job') AS job_json_keys
FROM jobs
WHERE source = 'WANTED'
  AND id >= @min_id
ORDER BY deadline_at IS NULL DESC, id DESC
LIMIT 10;
