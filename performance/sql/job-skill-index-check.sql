SELECT
    COUNT(*) AS indexed_skill_count,
    COUNT(DISTINCT job_id) AS indexed_job_count,
    COUNT(DISTINCT skill_id) AS indexed_distinct_skill_count
FROM job_skill_index;

SELECT
    requirement_type,
    COUNT(*) AS indexed_skill_count,
    COUNT(DISTINCT job_id) AS indexed_job_count,
    COUNT(DISTINCT skill_id) AS indexed_distinct_skill_count
FROM job_skill_index
GROUP BY requirement_type
ORDER BY requirement_type;

SELECT
    source,
    requirement_type,
    COUNT(*) AS indexed_skill_count,
    COUNT(DISTINCT job_id) AS indexed_job_count,
    COUNT(DISTINCT skill_id) AS indexed_distinct_skill_count
FROM job_skill_index
GROUP BY source, requirement_type
ORDER BY source, requirement_type;

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
  AND jsi.job_id IS NULL
ORDER BY j.source, j.id DESC
    LIMIT 30;
