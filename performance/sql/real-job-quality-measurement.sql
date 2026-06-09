-- Real job quality measurement for W4-8.
-- Run this after collecting JUMPIT/WANTED jobs.
--
-- Example:
--   docker compose exec -T mysql mysql -u jobflow -pjobflow jobflow < performance/sql/real-job-quality-measurement.sql

SET @min_id = 0;

WITH target_jobs AS (
    SELECT
        j.id,
        j.source,
        j.external_id,
        j.canonical_fingerprint,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.location_region,
        j.location_city,
        j.remote_type,
        j.opened_at,
        j.deadline_at,
        j.original_url,
        j.created_at,
        j.updated_at,
        COUNT(DISTINCT js.id) AS skill_count,
        COUNT(DISTINCT jet.id) AS experience_tag_count
    FROM jobs j
             LEFT JOIN job_skills js ON js.job_id = j.id
             LEFT JOIN job_experience_tags jet ON jet.job_id = j.id
    WHERE j.source IN ('JUMPIT', 'WANTED')
      AND j.id >= @min_id
    GROUP BY
        j.id,
        j.source,
        j.external_id,
        j.canonical_fingerprint,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.location_region,
        j.location_city,
        j.remote_type,
        j.opened_at,
        j.deadline_at,
        j.original_url,
        j.created_at,
        j.updated_at
)
SELECT
    source,
    COUNT(*) AS job_count,
    SUM(CASE WHEN external_id IS NULL OR external_id = '' THEN 1 ELSE 0 END) AS missing_external_id_count,
    SUM(CASE WHEN canonical_fingerprint IS NULL OR canonical_fingerprint = '' THEN 1 ELSE 0 END) AS missing_canonical_fingerprint_count,
    SUM(CASE WHEN title IS NULL OR title = '' THEN 1 ELSE 0 END) AS missing_title_count,
    SUM(CASE WHEN company_name IS NULL OR company_name = '' THEN 1 ELSE 0 END) AS missing_company_name_count,
    SUM(CASE WHEN original_url IS NULL OR original_url = '' THEN 1 ELSE 0 END) AS missing_original_url_count,
    SUM(CASE WHEN skill_count = 0 THEN 1 ELSE 0 END) AS zero_skill_count,
    ROUND(SUM(CASE WHEN skill_count = 0 THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS zero_skill_rate,
    SUM(CASE WHEN experience_tag_count = 0 THEN 1 ELSE 0 END) AS zero_experience_tag_count,
    ROUND(SUM(CASE WHEN experience_tag_count = 0 THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS zero_experience_tag_rate,
    SUM(CASE WHEN role = 'ETC' THEN 1 ELSE 0 END) AS etc_role_count,
    ROUND(SUM(CASE WHEN role = 'ETC' THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS etc_role_rate,
    SUM(CASE WHEN career_level = 'ANY' THEN 1 ELSE 0 END) AS any_career_level_count,
    ROUND(SUM(CASE WHEN career_level = 'ANY' THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS any_career_level_rate,
    SUM(CASE WHEN deadline_at IS NULL THEN 1 ELSE 0 END) AS missing_deadline_count,
    ROUND(SUM(CASE WHEN deadline_at IS NULL THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS missing_deadline_rate,
    SUM(CASE WHEN location_region IS NULL OR location_region = '' THEN 1 ELSE 0 END) AS missing_location_region_count,
    ROUND(SUM(CASE WHEN location_region IS NULL OR location_region = '' THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS missing_location_region_rate
FROM target_jobs
GROUP BY source
ORDER BY source;

WITH target_jobs AS (
    SELECT
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.deadline_at,
        j.location_region,
        j.location_city,
        j.original_url,
        COUNT(DISTINCT js.id) AS skill_count,
        COUNT(DISTINCT jet.id) AS experience_tag_count
    FROM jobs j
             LEFT JOIN job_skills js ON js.job_id = j.id
             LEFT JOIN job_experience_tags jet ON jet.job_id = j.id
    WHERE j.source IN ('JUMPIT', 'WANTED')
      AND j.id >= @min_id
    GROUP BY
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.deadline_at,
        j.location_region,
        j.location_city,
        j.original_url
)
SELECT
    source,
    id,
    external_id,
    title,
    company_name,
    role,
    career_level,
    skill_count,
    experience_tag_count,
    deadline_at,
    location_region,
    location_city,
    original_url
FROM target_jobs
WHERE skill_count = 0
   OR experience_tag_count = 0
   OR role = 'ETC'
   OR career_level = 'ANY'
   OR deadline_at IS NULL
   OR location_region IS NULL
   OR location_region = ''
ORDER BY source ASC, id DESC
    LIMIT 100;

SELECT
    source,
    external_id,
    COUNT(*) AS duplicate_count
FROM jobs
WHERE source IN ('JUMPIT', 'WANTED')
  AND id >= @min_id
GROUP BY source, external_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, source ASC, external_id ASC;

SELECT
    canonical_fingerprint,
    COUNT(*) AS duplicate_candidate_count,
    GROUP_CONCAT(CONCAT(source, ':', external_id) ORDER BY source, external_id SEPARATOR ', ') AS jobs
FROM jobs
WHERE source IN ('JUMPIT', 'WANTED')
  AND id >= @min_id
  AND canonical_fingerprint IS NOT NULL
  AND canonical_fingerprint <> ''
GROUP BY canonical_fingerprint
HAVING COUNT(*) > 1
ORDER BY duplicate_candidate_count DESC, canonical_fingerprint ASC
    LIMIT 100;

SELECT
    source,
    role,
    COUNT(*) AS job_count
FROM jobs
WHERE source IN ('JUMPIT', 'WANTED')
  AND id >= @min_id
GROUP BY source, role
ORDER BY source ASC, job_count DESC, role ASC;

SELECT
    source,
    career_level,
    COUNT(*) AS job_count
FROM jobs
WHERE source IN ('JUMPIT', 'WANTED')
  AND id >= @min_id
GROUP BY source, career_level
ORDER BY source ASC, job_count DESC, career_level ASC;

SELECT
    j.source,
    s.name AS skill_name,
    COUNT(*) AS job_count
FROM jobs j
         JOIN job_skills js ON js.job_id = j.id
         JOIN skills s ON s.id = js.skill_id
WHERE j.source IN ('JUMPIT', 'WANTED')
  AND j.id >= @min_id
GROUP BY j.source, s.name
ORDER BY j.source ASC, job_count DESC, s.name ASC
    LIMIT 50;

SELECT
    j.source,
    etc.code AS tag_code,
    etc.name AS tag_name,
    COUNT(*) AS job_count
FROM jobs j
         JOIN job_experience_tags jet ON jet.job_id = j.id
         JOIN experience_tag_codes etc ON etc.code = jet.tag_code
WHERE j.source IN ('JUMPIT', 'WANTED')
  AND j.id >= @min_id
GROUP BY j.source, etc.code, etc.name
ORDER BY j.source ASC, job_count DESC, etc.code ASC
    LIMIT 50;
