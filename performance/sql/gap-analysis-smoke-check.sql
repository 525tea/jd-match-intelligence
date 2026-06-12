-- gap analysis smoke check
-- DB Console에서 실행한다.
-- 목적:
-- - gap-analysis smoke fixture가 어떤 user_project_id를 만들었는지 확인한다.
-- - smoke 프로젝트가 smoke user 소유로 조회되는지 확인한다.
-- - smoke 프로젝트 skill snapshot이 비어 있지 않은지 확인한다.
-- - gap-analysis API가 사용할 job_skill_index가 real source 기준으로 준비됐는지 확인한다.

SELECT
    u.id AS user_id,
    u.email,
    up.id AS user_project_id,
    up.source_type,
    up.external_id AS project_external_id,
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
WHERE u.email = 'gap-smoke@example.com'
  AND up.source_type = 'GITHUB'
  AND up.external_id = 'gap-analysis-smoke-project';

SELECT
    u.id AS user_id,
    u.email,
    up.id AS user_project_id,
    up.external_id AS project_external_id,
    upa.id AS analysis_id,
    COUNT(ups.id) AS user_project_skill_count,
    GROUP_CONCAT(s.name ORDER BY s.name SEPARATOR ', ') AS user_project_skills
FROM users u
         JOIN user_projects up ON up.user_id = u.id
         JOIN user_project_analysis upa ON upa.user_project_id = up.id
         LEFT JOIN user_project_skills ups ON ups.analysis_id = upa.id
         LEFT JOIN skills s ON s.id = ups.skill_id
WHERE u.email = 'gap-smoke@example.com'
  AND up.source_type = 'GITHUB'
  AND up.external_id = 'gap-analysis-smoke-project'
  AND upa.analysis_version = 1
GROUP BY
    u.id,
    u.email,
    up.id,
    up.external_id,
    upa.id;

SELECT
    up.id AS user_project_id,
    up.external_id AS project_external_id,
    latest.id AS latest_analysis_id,
    latest.analysis_version,
    latest.analyzed_at,
    COUNT(ups.id) AS latest_analysis_skill_count
FROM users u
         JOIN user_projects up ON up.user_id = u.id
         LEFT JOIN user_project_analysis latest ON latest.id = (
             SELECT upa.id
             FROM user_project_analysis upa
             WHERE upa.user_project_id = up.id
             ORDER BY upa.analyzed_at DESC, upa.id DESC
             LIMIT 1
         )
         LEFT JOIN user_project_skills ups ON ups.analysis_id = latest.id
WHERE u.email = 'gap-smoke@example.com'
  AND up.source_type = 'GITHUB'
  AND up.external_id = 'gap-analysis-smoke-project'
GROUP BY
    up.id,
    up.external_id,
    latest.id,
    latest.analysis_version,
    latest.analyzed_at;

SELECT
    j.source,
    COUNT(DISTINCT j.id) AS open_job_count,
    COUNT(DISTINCT jsi.job_id) AS indexed_open_job_count,
    COUNT(DISTINCT CASE WHEN jsi.requirement_type = 'REQUIRED' THEN jsi.job_id END) AS required_indexed_open_job_count,
    COUNT(DISTINCT CASE WHEN jsi.requirement_type = 'PREFERRED' THEN jsi.job_id END) AS preferred_indexed_open_job_count,
    COUNT(jsi.id) AS indexed_skill_count
FROM jobs j
         LEFT JOIN job_skill_index jsi ON jsi.job_id = j.id
WHERE j.source IN ('JUMPIT', 'WANTED')
  AND j.status = 'OPEN'
GROUP BY j.source
ORDER BY j.source;

SELECT
    j.source,
    j.role,
    COUNT(DISTINCT j.id) AS indexed_open_job_count
FROM job_skill_index jsi
         JOIN jobs j ON j.id = jsi.job_id
WHERE j.source IN ('JUMPIT', 'WANTED')
  AND j.status = 'OPEN'
  AND j.role IN ('BACKEND', 'FULLSTACK', 'SOFTWARE_ENGINEER', 'DEVOPS')
GROUP BY j.source, j.role
ORDER BY j.source, indexed_open_job_count DESC, j.role;
