-- JD match smoke pre-check
-- DB Console에서 실행한다.
-- 목적:
-- - JD 매칭 API smoke에 사용할 user_project_id 후보를 찾는다.
-- - 최신 project analysis가 skill / experience tag를 가지고 있는지 확인한다.
-- - job_skill_index가 real source 기준으로 준비되어 있는지 확인한다.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

WITH latest_analysis AS (
    SELECT
        up.id AS user_project_id,
        up.external_id AS user_project_external_id,
        up.user_id,
        upa.id AS analysis_id,
        upa.analysis_version,
        upa.analyzed_at,
        ROW_NUMBER() OVER (
            PARTITION BY up.id
            ORDER BY upa.analyzed_at DESC, upa.id DESC
            ) AS rn
    FROM user_projects up
             JOIN user_project_analysis upa
                  ON upa.user_project_id = up.id
)
SELECT
    la.user_project_id,
    la.user_project_external_id,
    la.user_id,
    la.analysis_id,
    la.analysis_version,
    la.analyzed_at,
    COUNT(DISTINCT ups.skill_id) AS project_skill_count,
    COUNT(DISTINCT upet.tag_code) AS project_experience_tag_count,
    GROUP_CONCAT(DISTINCT s.name ORDER BY s.name SEPARATOR ', ') AS project_skills,
    GROUP_CONCAT(DISTINCT etc.code ORDER BY etc.code SEPARATOR ', ') AS project_experience_tags
FROM latest_analysis la
         LEFT JOIN user_project_skills ups
                   ON ups.analysis_id = la.analysis_id
         LEFT JOIN skills s
                   ON s.id = ups.skill_id
         LEFT JOIN user_project_experience_tags upet
                   ON upet.analysis_id = la.analysis_id
         LEFT JOIN experience_tag_codes etc
                   ON etc.code = upet.tag_code
WHERE la.rn = 1
GROUP BY
    la.user_project_id,
    la.user_project_external_id,
    la.user_id,
    la.analysis_id,
    la.analysis_version,
    la.analyzed_at
HAVING project_skill_count > 0
ORDER BY la.analyzed_at DESC, la.user_project_id DESC
LIMIT 10;

SELECT
    COUNT(*) AS indexed_skill_count,
    COUNT(DISTINCT job_id) AS indexed_job_count,
    COUNT(DISTINCT skill_id) AS indexed_distinct_skill_count
FROM job_skill_index
WHERE job_source IN ('JUMPIT', 'WANTED');

SELECT
    job_source,
    requirement_type,
    COUNT(*) AS indexed_skill_count,
    COUNT(DISTINCT job_id) AS indexed_job_count,
    COUNT(DISTINCT skill_id) AS indexed_distinct_skill_count
FROM job_skill_index
WHERE job_source IN ('JUMPIT', 'WANTED')
GROUP BY job_source, requirement_type
ORDER BY job_source, requirement_type;
