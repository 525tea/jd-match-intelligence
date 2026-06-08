SET @source = 'ZIGHANG';
SET @limit = 50;

SELECT
    COUNT(*) AS collected_job_count
FROM jobs
WHERE source = @source;

SELECT
    id,
    source,
    external_id,
    canonical_fingerprint,
    title,
    company_name,
    role,
    career_level,
    location_region,
    location_city,
    remote_type,
    deadline_at,
    original_url,
    created_at,
    updated_at
FROM jobs
WHERE source = @source
ORDER BY id DESC
    LIMIT 50;

SELECT
    COUNT(*) AS missing_external_id_count
FROM jobs
WHERE source = @source
  AND (external_id IS NULL OR external_id = '');

SELECT
    COUNT(*) AS missing_canonical_fingerprint_count
FROM jobs
WHERE source = @source
  AND (canonical_fingerprint IS NULL OR canonical_fingerprint = '');

SELECT
    COUNT(*) AS missing_title_count
FROM jobs
WHERE source = @source
  AND (title IS NULL OR title = '');

SELECT
    COUNT(*) AS missing_company_name_count
FROM jobs
WHERE source = @source
  AND (company_name IS NULL OR company_name = '');

SELECT
    COUNT(*) AS missing_original_url_count
FROM jobs
WHERE source = @source
  AND (original_url IS NULL OR original_url = '');

SELECT
    source,
    external_id,
    COUNT(*) AS duplicate_count
FROM jobs
WHERE source = @source
GROUP BY source, external_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, external_id ASC;

SELECT
    canonical_fingerprint,
    COUNT(*) AS same_fingerprint_count,
    GROUP_CONCAT(CONCAT(source, ':', external_id) ORDER BY source, external_id SEPARATOR ', ') AS jobs
FROM jobs
WHERE canonical_fingerprint IS NOT NULL
  AND canonical_fingerprint <> ''
GROUP BY canonical_fingerprint
HAVING COUNT(*) > 1
ORDER BY same_fingerprint_count DESC, canonical_fingerprint ASC
    LIMIT 50;

SELECT
    j.id,
    j.external_id,
    j.title,
    COUNT(js.id) AS skill_count
FROM jobs j
         LEFT JOIN job_skills js ON js.job_id = j.id
WHERE j.source = @source
GROUP BY j.id, j.external_id, j.title
ORDER BY j.id DESC
    LIMIT 50;

SELECT
    j.id,
    j.external_id,
    j.title,
    s.name AS skill_name,
    js.requirement_type
FROM jobs j
         JOIN job_skills js ON js.job_id = j.id
         JOIN skills s ON s.id = js.skill_id
WHERE j.source = @source
ORDER BY j.id DESC, s.name ASC
    LIMIT 100;

SELECT
    j.id,
    j.external_id,
    j.title,
    COUNT(jet.id) AS experience_tag_count
FROM jobs j
         LEFT JOIN job_experience_tags jet ON jet.job_id = j.id
WHERE j.source = @source
GROUP BY j.id, j.external_id, j.title
ORDER BY j.id DESC
    LIMIT 50;

SELECT
    j.id,
    j.external_id,
    j.title,
    etc.code AS tag_code,
    etc.name AS tag_name,
    jet.source_phrase
FROM jobs j
         JOIN job_experience_tags jet ON jet.job_id = j.id
         JOIN experience_tag_codes etc ON etc.code = jet.tag_code
WHERE j.source = @source
ORDER BY j.id DESC, etc.code ASC
    LIMIT 100;

SELECT
    j.id,
    j.external_id,
    j.title,
    j.role,
    j.role_detail,
    j.description
FROM jobs j
WHERE j.source = @source
  AND NOT EXISTS (
    SELECT 1
    FROM job_skills js
    WHERE js.job_id = j.id
)
ORDER BY j.id DESC
    LIMIT 50;

SELECT
    j.id,
    j.external_id,
    j.title,
    j.description
FROM jobs j
WHERE j.source = @source
  AND NOT EXISTS (
    SELECT 1
    FROM job_experience_tags jet
    WHERE jet.job_id = j.id
)
ORDER BY j.id DESC
    LIMIT 50;
