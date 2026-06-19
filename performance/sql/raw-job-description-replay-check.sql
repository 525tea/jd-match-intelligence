SET @min_id = 0;

WITH target_jobs AS (
    SELECT
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.description,
        j.raw_data,
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
        j.description,
        j.raw_data
),
     wanted_raw_section_flags AS (
         SELECT
             id,
             JSON_EXTRACT(raw_data, '$.job.detail.intro') IS NOT NULL AS has_intro,
             JSON_EXTRACT(raw_data, '$.job.detail.main_tasks') IS NOT NULL AS has_main_tasks,
             JSON_EXTRACT(raw_data, '$.job.detail.requirements') IS NOT NULL AS has_requirements,
             JSON_EXTRACT(raw_data, '$.job.detail.preferred_points') IS NOT NULL AS has_preferred_points,
             JSON_EXTRACT(raw_data, '$.job.detail.benefits') IS NOT NULL AS has_benefits,
             (
                 JSON_EXTRACT(raw_data, '$.job.detail.hiring_process') IS NOT NULL
                     OR JSON_EXTRACT(raw_data, '$.job.detail.recruitment_process') IS NOT NULL
                     OR JSON_EXTRACT(raw_data, '$.job.detail.selection_process') IS NOT NULL
                     OR JSON_EXTRACT(raw_data, '$.job.detail.interview_process') IS NOT NULL
                     OR JSON_EXTRACT(raw_data, '$.job.detail.process') IS NOT NULL
                     OR JSON_EXTRACT(raw_data, '$.job.detail.caution') IS NOT NULL
                     OR JSON_EXTRACT(raw_data, '$.job.detail.notice') IS NOT NULL
                 ) AS has_process
         FROM target_jobs
         WHERE source = 'WANTED'
     ),
     summary AS (
         SELECT
             tj.source,
             COUNT(*) AS job_count,
             SUM(CASE WHEN tj.raw_data IS NULL THEN 1 ELSE 0 END) AS missing_raw_data_count,
             SUM(CASE WHEN tj.description IS NULL OR tj.description = '' THEN 1 ELSE 0 END) AS blank_description_count,
             SUM(CASE WHEN tj.skill_count = 0 THEN 1 ELSE 0 END) AS zero_skill_job_count,
             SUM(CASE WHEN tj.experience_tag_count = 0 THEN 1 ELSE 0 END) AS zero_experience_tag_job_count,
             SUM(CASE
                     WHEN tj.source = 'JUMPIT'
                         AND JSON_EXTRACT(tj.raw_data, '$.rawBody') IS NULL
                         THEN 1
                     ELSE 0
                 END) AS jumpit_missing_raw_body_count,
             SUM(CASE
                     WHEN tj.source = 'WANTED'
                         AND wr.has_intro = 1
                         AND tj.description NOT LIKE '%[회사 소개]%'
                         THEN 1
                     ELSE 0
                 END) AS wanted_missing_intro_section_count,
             SUM(CASE
                     WHEN tj.source = 'WANTED'
                         AND wr.has_main_tasks = 1
                         AND tj.description NOT LIKE '%[주요 업무]%'
                         THEN 1
                     ELSE 0
                 END) AS wanted_missing_main_tasks_section_count,
             SUM(CASE
                     WHEN tj.source = 'WANTED'
                         AND wr.has_requirements = 1
                         AND tj.description NOT LIKE '%[자격 요건]%'
                         THEN 1
                     ELSE 0
                 END) AS wanted_missing_requirements_section_count,
             SUM(CASE
                     WHEN tj.source = 'WANTED'
                         AND wr.has_preferred_points = 1
                         AND tj.description NOT LIKE '%[우대 사항]%'
                         THEN 1
                     ELSE 0
                 END) AS wanted_missing_preferred_section_count,
             SUM(CASE
                     WHEN tj.source = 'WANTED'
                         AND wr.has_benefits = 1
                         AND tj.description NOT LIKE '%[혜택 및 복지]%'
                         THEN 1
                     ELSE 0
                 END) AS wanted_missing_benefits_section_count,
             SUM(CASE
                     WHEN tj.source = 'WANTED'
                         AND wr.has_process = 1
                         AND tj.description NOT LIKE '%[채용절차 및 기타 지원 유의사항]%'
                         THEN 1
                     ELSE 0
                 END) AS wanted_missing_process_section_count
         FROM target_jobs tj
                  LEFT JOIN wanted_raw_section_flags wr ON wr.id = tj.id
         GROUP BY tj.source
     )
SELECT
    source,
    job_count,
    missing_raw_data_count,
    blank_description_count,
    zero_skill_job_count,
    zero_experience_tag_job_count,
    jumpit_missing_raw_body_count,
    wanted_missing_intro_section_count,
    wanted_missing_main_tasks_section_count,
    wanted_missing_requirements_section_count,
    wanted_missing_preferred_section_count,
    wanted_missing_benefits_section_count,
    wanted_missing_process_section_count
FROM summary
ORDER BY source;

SELECT
    'WANTED section replay misses' AS check_name,
    j.id,
    j.external_id,
    j.title,
    CONCAT(
            CASE
                WHEN JSON_EXTRACT(j.raw_data, '$.job.detail.intro') IS NOT NULL
                    AND j.description NOT LIKE '%[회사 소개]%'
                    THEN 'intro '
                ELSE ''
                END,
            CASE
                WHEN JSON_EXTRACT(j.raw_data, '$.job.detail.main_tasks') IS NOT NULL
                    AND j.description NOT LIKE '%[주요 업무]%'
                    THEN 'main_tasks '
                ELSE ''
                END,
            CASE
                WHEN JSON_EXTRACT(j.raw_data, '$.job.detail.requirements') IS NOT NULL
                    AND j.description NOT LIKE '%[자격 요건]%'
                    THEN 'requirements '
                ELSE ''
                END,
            CASE
                WHEN JSON_EXTRACT(j.raw_data, '$.job.detail.preferred_points') IS NOT NULL
                    AND j.description NOT LIKE '%[우대 사항]%'
                    THEN 'preferred_points '
                ELSE ''
                END,
            CASE
                WHEN JSON_EXTRACT(j.raw_data, '$.job.detail.benefits') IS NOT NULL
                    AND j.description NOT LIKE '%[혜택 및 복지]%'
                    THEN 'benefits '
                ELSE ''
                END
    ) AS missing_sections
FROM jobs j
WHERE j.source = 'WANTED'
  AND j.id >= @min_id
  AND (
    (JSON_EXTRACT(j.raw_data, '$.job.detail.intro') IS NOT NULL AND j.description NOT LIKE '%[회사 소개]%')
        OR (JSON_EXTRACT(j.raw_data, '$.job.detail.main_tasks') IS NOT NULL AND j.description NOT LIKE '%[주요 업무]%')
        OR (JSON_EXTRACT(j.raw_data, '$.job.detail.requirements') IS NOT NULL AND j.description NOT LIKE '%[자격 요건]%')
        OR (JSON_EXTRACT(j.raw_data, '$.job.detail.preferred_points') IS NOT NULL AND j.description NOT LIKE '%[우대 사항]%')
        OR (JSON_EXTRACT(j.raw_data, '$.job.detail.benefits') IS NOT NULL AND j.description NOT LIKE '%[혜택 및 복지]%')
    )
ORDER BY j.id
    LIMIT 20;

SELECT
    'Description samples' AS check_name,
    j.source,
    j.id,
    j.external_id,
    j.title,
    LEFT(j.description, 1000) AS description_sample,
    COUNT(DISTINCT js.id) AS skill_count,
    COUNT(DISTINCT jet.id) AS experience_tag_count
FROM jobs j
    LEFT JOIN job_skills js ON js.job_id = j.id
    LEFT JOIN job_experience_tags jet ON jet.job_id = j.id
WHERE j.source IN ('JUMPIT', 'WANTED')
  AND j.id >= @min_id
GROUP BY
    j.source,
    j.id,
    j.external_id,
    j.title,
    j.description
ORDER BY j.source, j.id
    LIMIT 10;
