-- project inventory smoke check
-- DB Console에서 실행한다.
-- 목적:
-- - /projects/{userProjectId}/skills, /projects/{userProjectId}/experience-tags smoke 대상 프로젝트를 확인한다.
-- - 프로젝트가 소유자 user와 연결되어 있는지 확인한다.
-- - latest analysis 기준 skill/tag snapshot이 비어 있지 않은지 확인한다.
-- - 개인 이메일 없이 smoke project external_id 기준으로 조회한다.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @project_inventory_project_external_id := 'project-inventory-local-smoke-project';

SELECT
    u.id AS user_id,
    u.email,
    up.id AS user_project_id,
    up.source_type,
    up.external_id AS project_external_id,
    up.name AS project_name,
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM user_projects owned
            WHERE owned.id = up.id
              AND owned.user_id = u.id
        ) THEN 'PASS'
        ELSE 'FAIL'
        END AS owned_project_check
FROM users u
         JOIN user_projects up ON up.user_id = u.id
WHERE up.source_type = 'GITHUB'
  AND up.external_id = @project_inventory_project_external_id
ORDER BY up.id DESC;

SELECT
    up.id AS user_project_id,
    up.external_id AS project_external_id,
    latest.id AS latest_analysis_id,
    latest.analysis_version,
    latest.model_version,
    latest.source_hash,
    latest.analyzed_at,
    COUNT(DISTINCT ups.id) AS latest_analysis_skill_count,
    COUNT(DISTINCT upet.id) AS latest_analysis_experience_tag_count,
    GROUP_CONCAT(DISTINCT s.name ORDER BY s.name SEPARATOR ', ') AS latest_analysis_skills,
    GROUP_CONCAT(DISTINCT etc.code ORDER BY etc.code SEPARATOR ', ') AS latest_analysis_experience_tags
FROM user_projects up
         LEFT JOIN user_project_analysis latest ON latest.id = (
    SELECT upa.id
    FROM user_project_analysis upa
    WHERE upa.user_project_id = up.id
    ORDER BY upa.analyzed_at DESC, upa.id DESC
    LIMIT 1
    )
    LEFT JOIN user_project_skills ups ON ups.analysis_id = latest.id
    LEFT JOIN skills s ON s.id = ups.skill_id
    LEFT JOIN user_project_experience_tags upet ON upet.analysis_id = latest.id
    LEFT JOIN experience_tag_codes etc ON etc.code = upet.tag_code
WHERE up.source_type = 'GITHUB'
  AND up.external_id = @project_inventory_project_external_id
GROUP BY
    up.id,
    up.external_id,
    latest.id,
    latest.analysis_version,
    latest.model_version,
    latest.source_hash,
    latest.analyzed_at
ORDER BY up.id DESC;

SELECT
    up.id AS user_project_id,
    ups.id AS project_skill_id,
    s.id AS skill_id,
    s.name AS skill_name,
    s.normalized_name,
    s.category,
    ups.source,
    ups.confidence,
    ups.evidence
FROM user_projects up
         JOIN user_project_analysis latest ON latest.id = (
    SELECT upa.id
    FROM user_project_analysis upa
    WHERE upa.user_project_id = up.id
    ORDER BY upa.analyzed_at DESC, upa.id DESC
    LIMIT 1
    )
    JOIN user_project_skills ups ON ups.analysis_id = latest.id
    JOIN skills s ON s.id = ups.skill_id
WHERE up.source_type = 'GITHUB'
  AND up.external_id = @project_inventory_project_external_id
ORDER BY s.category, s.name;

SELECT
    up.id AS user_project_id,
    upet.id AS project_experience_tag_id,
    etc.code AS tag_code,
    etc.name AS tag_name,
    etc.description,
    upet.confidence,
    upet.evidence
FROM user_projects up
         JOIN user_project_analysis latest ON latest.id = (
    SELECT upa.id
    FROM user_project_analysis upa
    WHERE upa.user_project_id = up.id
    ORDER BY upa.analyzed_at DESC, upa.id DESC
    LIMIT 1
    )
    JOIN user_project_experience_tags upet ON upet.analysis_id = latest.id
    JOIN experience_tag_codes etc ON etc.code = upet.tag_code
WHERE up.source_type = 'GITHUB'
  AND up.external_id = @project_inventory_project_external_id
ORDER BY etc.code;
