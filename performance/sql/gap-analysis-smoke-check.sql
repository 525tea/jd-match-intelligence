-- gap analysis smoke check
-- DB Console에서 실행한다.
-- 목적:
-- - gap-analysis smoke fixture가 어떤 user_project_id를 만들었는지 확인한다.
-- - smoke 프로젝트가 smoke user 소유로 조회되는지 확인한다.
-- - smoke 프로젝트 skill snapshot이 비어 있지 않은지 확인한다.
-- - gap-analysis API가 사용할 job_skill_index가 real source 기준으로 준비됐는지 확인한다.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

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

WITH target_roles AS (
    SELECT 'BACKEND' AS role
    UNION ALL SELECT 'FULLSTACK'
    UNION ALL SELECT 'SOFTWARE_ENGINEER'
    UNION ALL SELECT 'DEVOPS'
),
     indexed_job_bucket AS (
         SELECT
             j.id AS job_id,
             j.source,
             j.external_id,
             j.title,
             j.company_name,
             j.role,
             SUM(CASE WHEN jsi.requirement_type = 'REQUIRED' THEN 1 ELSE 0 END) AS required_skill_count,
             SUM(CASE WHEN jsi.requirement_type = 'PREFERRED' THEN 1 ELSE 0 END) AS preferred_skill_count
         FROM jobs j
                  JOIN job_skill_index jsi ON jsi.job_id = j.id
                  JOIN target_roles tr ON tr.role = j.role
         WHERE j.source IN ('JUMPIT', 'WANTED')
           AND j.status = 'OPEN'
         GROUP BY
             j.id,
             j.source,
             j.external_id,
             j.title,
             j.company_name,
             j.role
     )
SELECT
    source,
    COUNT(*) AS indexed_target_role_job_count,
    SUM(CASE WHEN required_skill_count > 0 THEN 1 ELSE 0 END) AS required_bucket_job_count,
    SUM(CASE WHEN preferred_skill_count > 0 THEN 1 ELSE 0 END) AS preferred_bucket_job_count,
    SUM(CASE WHEN required_skill_count = 0 AND preferred_skill_count > 0 THEN 1 ELSE 0 END) AS preferred_only_job_count,
    SUM(CASE WHEN required_skill_count > 0 AND preferred_skill_count = 0 THEN 1 ELSE 0 END) AS required_only_job_count,
    SUM(CASE WHEN required_skill_count > 0 AND preferred_skill_count > 0 THEN 1 ELSE 0 END) AS both_bucket_job_count
FROM indexed_job_bucket
GROUP BY source
ORDER BY source;

WITH target_roles AS (
    SELECT 'BACKEND' AS role
    UNION ALL SELECT 'FULLSTACK'
    UNION ALL SELECT 'SOFTWARE_ENGINEER'
    UNION ALL SELECT 'DEVOPS'
),
     indexed_job_bucket AS (
         SELECT
             j.id AS job_id,
             j.source,
             j.external_id,
             j.title,
             j.company_name,
             j.role,
             j.career_level,
             j.original_url,
             SUM(CASE WHEN jsi.requirement_type = 'REQUIRED' THEN 1 ELSE 0 END) AS required_skill_count,
             SUM(CASE WHEN jsi.requirement_type = 'PREFERRED' THEN 1 ELSE 0 END) AS preferred_skill_count
         FROM jobs j
                  JOIN job_skill_index jsi ON jsi.job_id = j.id
                  JOIN target_roles tr ON tr.role = j.role
         WHERE j.source IN ('JUMPIT', 'WANTED')
           AND j.status = 'OPEN'
         GROUP BY
             j.id,
             j.source,
             j.external_id,
             j.title,
             j.company_name,
             j.role,
             j.career_level,
             j.original_url
     )
SELECT
    source,
    job_id,
    external_id,
    title,
    company_name,
    role,
    career_level,
    required_skill_count,
    preferred_skill_count,
    original_url
FROM indexed_job_bucket
WHERE required_skill_count = 0
  AND preferred_skill_count > 0
ORDER BY preferred_skill_count DESC, job_id DESC
LIMIT 20;

SELECT
    'skill_trends' AS evidence_source,
    MAX(period_start) AS latest_period_start,
    COUNT(*) AS row_count
FROM skill_trends
WHERE period_type = 'MONTHLY'
UNION ALL
SELECT
    'skill_cooccurrence' AS evidence_source,
    MAX(period_start) AS latest_period_start,
    COUNT(*) AS row_count
FROM skill_cooccurrence
WHERE period_type = 'MONTHLY'
UNION ALL
SELECT
    'skill_experience_market' AS evidence_source,
    MAX(period_start) AS latest_period_start,
    COUNT(*) AS row_count
FROM skill_experience_market
WHERE period_type = 'MONTHLY';

SELECT
    COUNT(*) AS supported_cooccurrence_count
FROM skill_cooccurrence sc
WHERE sc.period_type = 'MONTHLY'
  AND sc.period_start = (
    SELECT MAX(period_start)
    FROM skill_cooccurrence
    WHERE period_type = 'MONTHLY'
)
  AND sc.cooccurrence_count >= 3;

SELECT
    COUNT(*) AS supported_skill_experience_market_count
FROM skill_experience_market sem
WHERE sem.period_type = 'MONTHLY'
  AND sem.period_start = (
    SELECT MAX(period_start)
    FROM skill_experience_market
    WHERE period_type = 'MONTHLY'
)
  AND sem.job_count >= 3;

WITH smoke_project_skills AS (
    SELECT DISTINCT ups.skill_id
    FROM users u
             JOIN user_projects up ON up.user_id = u.id
             JOIN user_project_analysis upa ON upa.user_project_id = up.id
             JOIN user_project_skills ups ON ups.analysis_id = upa.id
    WHERE u.email = 'gap-smoke@example.com'
      AND up.external_id = 'gap-analysis-smoke-project'
      AND upa.id = (
        SELECT latest.id
        FROM user_project_analysis latest
        WHERE latest.user_project_id = up.id
        ORDER BY latest.analyzed_at DESC, latest.id DESC
    LIMIT 1
    )
    ),
    target_roles AS (
SELECT 'BACKEND' AS role
UNION ALL SELECT 'FULLSTACK'
UNION ALL SELECT 'SOFTWARE_ENGINEER'
UNION ALL SELECT 'DEVOPS'
    ),
    missing_skills AS (
SELECT DISTINCT
    s.id AS skill_id,
    s.name AS skill_name
FROM job_skill_index jsi
    JOIN jobs j ON j.id = jsi.job_id
    JOIN target_roles tr ON tr.role = j.role
    JOIN skills s ON s.id = jsi.skill_id
    LEFT JOIN smoke_project_skills sps ON sps.skill_id = s.id
WHERE j.source IN ('JUMPIT', 'WANTED')
  AND j.status = 'OPEN'
  AND sps.skill_id IS NULL
    )
SELECT
    ms.skill_name,
    COALESCE(st.job_count, 0) AS added_jobs,
    COUNT(DISTINCT sc.id) AS supported_cooccurrence_count,
    COUNT(DISTINCT sem.id) AS supported_related_tag_count
FROM missing_skills ms
         LEFT JOIN skill_trends st
                   ON st.skill_id = ms.skill_id
                       AND st.period_type = 'MONTHLY'
                       AND st.period_start = (
                           SELECT MAX(period_start)
                           FROM skill_trends
                           WHERE period_type = 'MONTHLY'
                       )
         LEFT JOIN skill_cooccurrence sc
                   ON sc.base_skill_id = ms.skill_id
                       AND sc.period_type = 'MONTHLY'
                       AND sc.period_start = (
                           SELECT MAX(period_start)
                           FROM skill_cooccurrence
                           WHERE period_type = 'MONTHLY'
                       )
                       AND sc.cooccurrence_count >= 3
         LEFT JOIN skill_experience_market sem
                   ON sem.skill_id = ms.skill_id
                       AND sem.period_type = 'MONTHLY'
                       AND sem.period_start = (
                           SELECT MAX(period_start)
                           FROM skill_experience_market
                           WHERE period_type = 'MONTHLY'
                       )
                       AND sem.job_count >= 3
GROUP BY
    ms.skill_id,
    ms.skill_name,
    st.job_count
HAVING added_jobs > 0
    OR supported_cooccurrence_count > 0
    OR supported_related_tag_count > 0
ORDER BY
    added_jobs DESC,
    supported_cooccurrence_count DESC,
    supported_related_tag_count DESC,
    ms.skill_name
    LIMIT 30;
