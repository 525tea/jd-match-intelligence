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
        j.deadline_at,
        j.location_region,
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
        j.canonical_fingerprint,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.deadline_at,
        j.location_region,
        j.original_url
),
     summary AS (
         SELECT
             source,
             COUNT(*) AS job_count,
             SUM(CASE WHEN external_id IS NULL OR external_id = '' THEN 1 ELSE 0 END) AS missing_external_id_count,
             SUM(CASE WHEN canonical_fingerprint IS NULL OR canonical_fingerprint = '' THEN 1 ELSE 0 END) AS missing_canonical_fingerprint_count,
             SUM(CASE WHEN title IS NULL OR title = '' THEN 1 ELSE 0 END) AS missing_title_count,
             SUM(CASE WHEN company_name IS NULL OR company_name = '' THEN 1 ELSE 0 END) AS missing_company_name_count,
             SUM(CASE WHEN original_url IS NULL OR original_url = '' THEN 1 ELSE 0 END) AS missing_original_url_count,
             ROUND(SUM(CASE WHEN skill_count = 0 THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS zero_skill_rate,
             ROUND(SUM(CASE WHEN experience_tag_count = 0 THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS zero_experience_tag_rate,
             ROUND(SUM(CASE WHEN role = 'ETC' THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS etc_role_rate,
             ROUND(SUM(CASE WHEN career_level = 'ANY' THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS any_career_level_rate,
             ROUND(SUM(CASE WHEN deadline_at IS NULL THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS missing_deadline_rate,
             ROUND(SUM(CASE WHEN location_region IS NULL OR location_region = '' THEN 1 ELSE 0 END) / COUNT(*) * 100, 2) AS missing_location_region_rate
         FROM target_jobs
         GROUP BY source
     )
SELECT
    source,
    metric,
    metric_value,
    warn_threshold,
    fail_threshold,
    CASE
        WHEN fail_direction = 'GT' AND metric_value > fail_threshold THEN 'FAIL'
        WHEN fail_direction = 'GT' AND metric_value > warn_threshold THEN 'WARN'
        WHEN fail_direction = 'COUNT_GT_ZERO' AND metric_value > 0 THEN 'FAIL'
        ELSE 'PASS'
        END AS status,
    description
FROM (
         SELECT source, 'missing_external_id_count' AS metric, missing_external_id_count AS metric_value,
                0 AS warn_threshold, 0 AS fail_threshold, 'COUNT_GT_ZERO' AS fail_direction,
                'source + externalId dedupe 필수값 누락' AS description
         FROM summary
         UNION ALL
         SELECT source, 'missing_canonical_fingerprint_count', missing_canonical_fingerprint_count,
                0, 0, 'COUNT_GT_ZERO',
                'source 간 중복 후보 탐지 fingerprint 누락'
         FROM summary
         UNION ALL
         SELECT source, 'missing_title_count', missing_title_count,
                0, 0, 'COUNT_GT_ZERO',
                '검색/매칭 핵심 title 누락'
         FROM summary
         UNION ALL
         SELECT source, 'missing_company_name_count', missing_company_name_count,
                0, 0, 'COUNT_GT_ZERO',
                '회사명 누락'
         FROM summary
         UNION ALL
         SELECT source, 'missing_original_url_count', missing_original_url_count,
                0, 0, 'COUNT_GT_ZERO',
                '원본 공고 URL 누락'
         FROM summary
         UNION ALL
         SELECT source, 'zero_skill_rate', zero_skill_rate,
                15, 30, 'GT',
                '정규화 skill이 하나도 없는 공고 비율'
         FROM summary
         UNION ALL
         SELECT source, 'zero_experience_tag_rate', zero_experience_tag_rate,
                40, 60, 'GT',
                '정규화 experience tag가 하나도 없는 공고 비율'
         FROM summary
         UNION ALL
         SELECT source, 'etc_role_rate', etc_role_rate,
                5, 10, 'GT',
                'role이 ETC로 남은 공고 비율'
         FROM summary
         UNION ALL
         SELECT source, 'any_career_level_rate', any_career_level_rate,
                20, 40, 'GT',
                'careerLevel이 ANY로 남은 공고 비율'
         FROM summary
         UNION ALL
         SELECT source, 'missing_deadline_rate', missing_deadline_rate,
                30, 60, 'GT',
                'deadlineAt 누락 공고 비율'
         FROM summary
         UNION ALL
         SELECT source, 'missing_location_region_rate', missing_location_region_rate,
                10, 25, 'GT',
                'locationRegion 누락 공고 비율'
         FROM summary
     ) gate
ORDER BY source ASC, FIELD(status, 'FAIL', 'WARN', 'PASS'), metric ASC;
