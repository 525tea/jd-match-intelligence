-- Real job zero-experience-tag analysis for jd_phrase_tag_mapping improvement.
-- Run in an IntelliJ/MySQL console after collecting JUMPIT/WANTED jobs and applying skill/role backfill.
--
-- Parameters:
--   @min_id: only analyze jobs with id >= this value.
--   @source_filter: one of 'ALL', 'JUMPIT', 'WANTED'.

SET @min_id = 0;
SET @source_filter = CONVERT('ALL' USING utf8mb4) COLLATE utf8mb4_unicode_ci;

WITH target_jobs AS (
    SELECT
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.role_detail,
        j.description,
        j.raw_data,
        j.original_url,
        COUNT(DISTINCT js.id) AS skill_count,
        COUNT(DISTINCT jet.id) AS experience_tag_count
    FROM jobs j
             LEFT JOIN job_skills js ON js.job_id = j.id
             LEFT JOIN job_experience_tags jet ON jet.job_id = j.id
    WHERE j.source IN ('JUMPIT', 'WANTED')
      AND j.id >= @min_id
      AND (@source_filter = 'ALL' OR j.source = @source_filter)
    GROUP BY
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.role_detail,
        j.description,
        j.raw_data,
        j.original_url
),
     zero_experience_tag_jobs AS (
         SELECT *
         FROM target_jobs
         WHERE experience_tag_count = 0
     )
SELECT
    source,
    COUNT(*) AS zero_experience_tag_count
FROM zero_experience_tag_jobs
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
        j.role_detail,
        j.description,
        j.raw_data,
        j.original_url,
        COUNT(DISTINCT js.id) AS skill_count,
        COUNT(DISTINCT jet.id) AS experience_tag_count
    FROM jobs j
             LEFT JOIN job_skills js ON js.job_id = j.id
             LEFT JOIN job_experience_tags jet ON jet.job_id = j.id
    WHERE j.source IN ('JUMPIT', 'WANTED')
      AND j.id >= @min_id
      AND (@source_filter = 'ALL' OR j.source = @source_filter)
    GROUP BY
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.role,
        j.career_level,
        j.role_detail,
        j.description,
        j.raw_data,
        j.original_url
),
     zero_experience_tag_jobs AS (
         SELECT *
         FROM target_jobs
         WHERE experience_tag_count = 0
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
    role_detail,
    LEFT(REGEXP_REPLACE(COALESCE(description, ''), '[[:space:]]+', ' '), 700) AS description_preview,
    original_url
FROM zero_experience_tag_jobs
ORDER BY source ASC, id DESC
LIMIT 120;

WITH target_jobs AS (
    SELECT
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.role,
        j.description,
        j.raw_data,
        COUNT(DISTINCT jet.id) AS experience_tag_count
    FROM jobs j
             LEFT JOIN job_experience_tags jet ON jet.job_id = j.id
    WHERE j.source IN ('JUMPIT', 'WANTED')
      AND j.id >= @min_id
      AND (@source_filter = 'ALL' OR j.source = @source_filter)
    GROUP BY
        j.id,
        j.source,
        j.external_id,
        j.title,
        j.company_name,
        j.role,
        j.description,
        j.raw_data
),
     zero_experience_tag_jobs AS (
         SELECT
             source,
             id,
             external_id,
             title,
             company_name,
             role,
             LOWER(REGEXP_REPLACE(CONCAT_WS(' ', title, description, raw_data), '[[:space:]]+', ' ')) AS normalized_text
         FROM target_jobs
         WHERE experience_tag_count = 0
     ),
     phrase_candidates AS (
         SELECT '성능 검증' AS phrase, 'PERFORMANCE' AS tag_code UNION ALL
         SELECT '성능 개선', 'PERFORMANCE' UNION ALL
         SELECT '성능 최적화', 'PERFORMANCE' UNION ALL
         SELECT '최적화', 'PERFORMANCE' UNION ALL
         SELECT '고도화', 'PERFORMANCE' UNION ALL
         SELECT '알고리즘 최적화', 'PERFORMANCE' UNION ALL
         SELECT '추론 성능', 'PERFORMANCE' UNION ALL
         SELECT '시뮬레이션', 'TESTING' UNION ALL
         SELECT '테스트', 'TESTING' UNION ALL
         SELECT '검증', 'TESTING' UNION ALL
         SELECT '문제 정의', 'RELIABILITY' UNION ALL
         SELECT '문제 해결', 'RELIABILITY' UNION ALL
         SELECT '이슈', 'RELIABILITY' UNION ALL
         SELECT '재현성 확보', 'RELIABILITY' UNION ALL
         SELECT '유지보수', 'RELIABILITY' UNION ALL
         SELECT '기술지원', 'RELIABILITY' UNION ALL
         SELECT '기술 지원', 'RELIABILITY' UNION ALL
         SELECT '운영 경험', 'RELIABILITY' UNION ALL
         SELECT '시스템 통합', 'CLOUD_INFRA' UNION ALL
         SELECT '환경 구축', 'CLOUD_INFRA' UNION ALL
         SELECT '인프라', 'CLOUD_INFRA' UNION ALL
         SELECT '데이터 파이프라인', 'CLOUD_INFRA' UNION ALL
         SELECT '파이프라인 구축', 'CLOUD_INFRA' UNION ALL
         SELECT '자동화 인프라', 'CLOUD_INFRA' UNION ALL
         SELECT '배포', 'CI_CD' UNION ALL
         SELECT '모델 배포', 'CI_CD' UNION ALL
         SELECT '서비스 배포', 'CI_CD' UNION ALL
         SELECT '모니터링', 'MONITORING' UNION ALL
         SELECT '대시보드', 'MONITORING' UNION ALL
         SELECT '보안 취약점', 'SECURITY' UNION ALL
         SELECT '인증 대응', 'SECURITY' UNION ALL
         SELECT '위협 모델링', 'SECURITY' UNION ALL
         SELECT '위험 평가', 'SECURITY' UNION ALL
         SELECT '보안 요구사항', 'SECURITY' UNION ALL
         SELECT '대용량', 'HIGH_TRAFFIC' UNION ALL
         SELECT '대규모', 'HIGH_TRAFFIC' UNION ALL
         SELECT '확장', 'SCALABILITY' UNION ALL
         SELECT '확장성', 'SCALABILITY'
     )
SELECT
    z.source,
    c.tag_code,
    c.phrase,
    COUNT(*) AS matched_zero_experience_tag_job_count,
    GROUP_CONCAT(CONCAT(z.external_id, ':', z.title) ORDER BY z.id DESC SEPARATOR ' | ') AS examples
FROM zero_experience_tag_jobs z
         JOIN phrase_candidates c
              ON z.normalized_text LIKE CONCAT('%', LOWER(c.phrase), '%')
GROUP BY z.source, c.tag_code, c.phrase
ORDER BY z.source ASC, matched_zero_experience_tag_job_count DESC, c.tag_code ASC, c.phrase ASC;
