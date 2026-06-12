-- job_skill_index verification
-- 1) 전체 인덱스 적재 상태
SELECT
    COUNT(*) AS indexed_skill_count,
    COUNT(DISTINCT job_id) AS indexed_job_count,
    COUNT(DISTINCT skill_id) AS indexed_distinct_skill_count
FROM job_skill_index;

-- 2) 실제 수집 source 기준 requirement type 분포
SELECT
    requirement_type,
    COUNT(*) AS indexed_skill_count,
    COUNT(DISTINCT job_id) AS indexed_job_count,
    COUNT(DISTINCT skill_id) AS indexed_distinct_skill_count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS indexed_skill_rate
FROM job_skill_index
WHERE source IN ('JUMPIT', 'WANTED')
GROUP BY requirement_type
ORDER BY requirement_type;

-- 3) 실제 수집 source별 requirement type 분포
SELECT
    source,
    requirement_type,
    COUNT(*) AS indexed_skill_count,
    COUNT(DISTINCT job_id) AS indexed_job_count,
    COUNT(DISTINCT skill_id) AS indexed_distinct_skill_count,
    ROUND(
            COUNT(*) * 100.0
                / SUM(COUNT(*)) OVER (PARTITION BY source),
            2
    ) AS source_indexed_skill_rate
FROM job_skill_index
WHERE source IN ('JUMPIT', 'WANTED')
GROUP BY source, requirement_type
ORDER BY source, requirement_type;

-- 4) 실제 수집 source 기준 open job coverage
SELECT
    j.source,
    COUNT(DISTINCT j.id) AS open_job_count,
    COUNT(DISTINCT jsi.job_id) AS indexed_open_job_count,
    COUNT(DISTINCT CASE WHEN jsi.job_id IS NULL THEN j.id END) AS open_job_without_index_count,
    ROUND(
            COUNT(DISTINCT jsi.job_id) * 100.0 / COUNT(DISTINCT j.id),
            2
    ) AS indexed_open_job_rate
FROM jobs j
         LEFT JOIN job_skill_index jsi ON jsi.job_id = j.id
WHERE j.status = 'OPEN'
  AND j.source IN ('JUMPIT', 'WANTED')
GROUP BY j.source
ORDER BY j.source;

-- 5) 실제 수집 source 중 skill index가 없는 open job 샘플
SELECT
    j.source,
    j.id,
    j.external_id,
    j.title,
    j.company_name,
    j.role,
    j.career_level,
    j.original_url
FROM jobs j
         LEFT JOIN job_skill_index jsi ON jsi.job_id = j.id
WHERE j.status = 'OPEN'
  AND j.source IN ('JUMPIT', 'WANTED')
  AND jsi.job_id IS NULL
ORDER BY j.source, j.id DESC
    LIMIT 30;

-- 6) 참고용: 전체 source별 coverage
SELECT
    j.source,
    COUNT(DISTINCT j.id) AS open_job_count,
    COUNT(DISTINCT jsi.job_id) AS indexed_open_job_count,
    COUNT(DISTINCT CASE WHEN jsi.job_id IS NULL THEN j.id END) AS open_job_without_index_count
FROM jobs j
         LEFT JOIN job_skill_index jsi ON jsi.job_id = j.id
WHERE j.status = 'OPEN'
GROUP BY j.source
ORDER BY j.source;
