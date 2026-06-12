-- job_skill_index match smoke
-- 목적:
-- - Gap Analysis API 구현 전에 job_skill_index만으로 required/preferred hit/miss를 계산할 수 있는지 확인한다.
-- - 이 쿼리는 backend-oriented sample user skill set 기준이다.
-- - target_roles를 바꾸면 다른 희망 직무 시나리오도 검증할 수 있다.

WITH user_skill_names AS (
    SELECT 'Java' AS skill_name
    UNION ALL SELECT 'Spring Boot'
    UNION ALL SELECT 'Spring Framework'
    UNION ALL SELECT 'JPA'
    UNION ALL SELECT 'MySQL'
    UNION ALL SELECT 'Redis'
    UNION ALL SELECT 'Docker'
    UNION ALL SELECT 'AWS'
    UNION ALL SELECT 'Git'
),
     target_roles AS (
         SELECT 'BACKEND' AS role
         UNION ALL SELECT 'FULLSTACK'
         UNION ALL SELECT 'SOFTWARE_ENGINEER'
         UNION ALL SELECT 'DEVOPS'
     ),
     user_skills AS (
         SELECT
             s.id,
             s.name
         FROM skills s
                  JOIN user_skill_names usn ON usn.skill_name = s.name
     ),
     match_summary AS (
         SELECT
             jsi.job_id,
             MAX(jsi.source) AS source,
             MAX(j.external_id) AS external_id,
             MAX(j.title) AS title,
             MAX(j.company_name) AS company_name,
             MAX(j.role) AS role,
             MAX(j.career_level) AS career_level,
             MAX(j.original_url) AS original_url,
             SUM(CASE WHEN jsi.requirement_type = 'REQUIRED' THEN 1 ELSE 0 END) AS required_skill_count,
             SUM(CASE WHEN jsi.requirement_type = 'REQUIRED' AND us.id IS NOT NULL THEN 1 ELSE 0 END) AS matched_required_skill_count,
             SUM(CASE WHEN jsi.requirement_type = 'PREFERRED' THEN 1 ELSE 0 END) AS preferred_skill_count,
             SUM(CASE WHEN jsi.requirement_type = 'PREFERRED' AND us.id IS NOT NULL THEN 1 ELSE 0 END) AS matched_preferred_skill_count
         FROM job_skill_index jsi
                  JOIN jobs j ON j.id = jsi.job_id
                  JOIN target_roles tr ON tr.role = j.role
                  LEFT JOIN user_skills us ON us.id = jsi.skill_id
         WHERE jsi.job_status = 'OPEN'
           AND jsi.source IN ('JUMPIT', 'WANTED')
         GROUP BY jsi.job_id
     ),
     matched_required AS (
         SELECT
             jsi.job_id,
             GROUP_CONCAT(DISTINCT s.name ORDER BY s.name SEPARATOR ', ') AS matched_required_skills
         FROM job_skill_index jsi
                  JOIN jobs j ON j.id = jsi.job_id
                  JOIN skills s ON s.id = jsi.skill_id
                  JOIN user_skills us ON us.id = jsi.skill_id
                  JOIN target_roles tr ON tr.role = j.role
         WHERE jsi.requirement_type = 'REQUIRED'
           AND jsi.job_status = 'OPEN'
           AND jsi.source IN ('JUMPIT', 'WANTED')
         GROUP BY jsi.job_id
     ),
     missing_required AS (
         SELECT
             jsi.job_id,
             GROUP_CONCAT(DISTINCT s.name ORDER BY s.name SEPARATOR ', ') AS missing_required_skills
         FROM job_skill_index jsi
                  JOIN jobs j ON j.id = jsi.job_id
                  JOIN skills s ON s.id = jsi.skill_id
                  JOIN target_roles tr ON tr.role = j.role
                  LEFT JOIN user_skills us ON us.id = jsi.skill_id
         WHERE jsi.requirement_type = 'REQUIRED'
           AND us.id IS NULL
           AND jsi.job_status = 'OPEN'
           AND jsi.source IN ('JUMPIT', 'WANTED')
         GROUP BY jsi.job_id
     ),
     matched_preferred AS (
         SELECT
             jsi.job_id,
             GROUP_CONCAT(DISTINCT s.name ORDER BY s.name SEPARATOR ', ') AS matched_preferred_skills
         FROM job_skill_index jsi
                  JOIN jobs j ON j.id = jsi.job_id
                  JOIN skills s ON s.id = jsi.skill_id
                  JOIN user_skills us ON us.id = jsi.skill_id
                  JOIN target_roles tr ON tr.role = j.role
         WHERE jsi.requirement_type = 'PREFERRED'
           AND jsi.job_status = 'OPEN'
           AND jsi.source IN ('JUMPIT', 'WANTED')
         GROUP BY jsi.job_id
     ),
     missing_preferred AS (
         SELECT
             jsi.job_id,
             GROUP_CONCAT(DISTINCT s.name ORDER BY s.name SEPARATOR ', ') AS missing_preferred_skills
         FROM job_skill_index jsi
                  JOIN jobs j ON j.id = jsi.job_id
                  JOIN skills s ON s.id = jsi.skill_id
                  JOIN target_roles tr ON tr.role = j.role
                  LEFT JOIN user_skills us ON us.id = jsi.skill_id
         WHERE jsi.requirement_type = 'PREFERRED'
           AND us.id IS NULL
           AND jsi.job_status = 'OPEN'
           AND jsi.source IN ('JUMPIT', 'WANTED')
         GROUP BY jsi.job_id
     ),
     scored AS (
         SELECT
             ms.*,
             ms.required_skill_count - ms.matched_required_skill_count AS missing_required_skill_count,
             ms.preferred_skill_count - ms.matched_preferred_skill_count AS missing_preferred_skill_count,
             CASE
                 WHEN ms.required_skill_count = 0 THEN NULL
                 ELSE ms.matched_required_skill_count * 100.0 / ms.required_skill_count
                 END AS required_match_rate,
             CASE
                 WHEN ms.preferred_skill_count = 0 THEN NULL
                 ELSE ms.matched_preferred_skill_count * 100.0 / ms.preferred_skill_count
                 END AS preferred_match_rate,
             (
                 ms.matched_required_skill_count * 10.0
                     + CASE
                           WHEN ms.required_skill_count = 0 THEN 0
                           ELSE ms.matched_required_skill_count * 50.0 / ms.required_skill_count
                     END
                     + ms.matched_preferred_skill_count * 3.0
                     + CASE
                           WHEN ms.preferred_skill_count = 0 THEN 0
                           ELSE ms.matched_preferred_skill_count * 10.0 / ms.preferred_skill_count
                     END
                     - (ms.required_skill_count - ms.matched_required_skill_count) * 3.0
                 ) AS match_score
         FROM match_summary ms
         WHERE ms.required_skill_count > 0
            OR ms.preferred_skill_count > 0
     )
SELECT
    s.source,
    s.job_id,
    s.external_id,
    s.title,
    s.company_name,
    s.role,
    s.career_level,
    s.required_skill_count,
    s.matched_required_skill_count,
    s.missing_required_skill_count,
    ROUND(s.required_match_rate, 2) AS required_match_rate,
    s.preferred_skill_count,
    s.matched_preferred_skill_count,
    s.missing_preferred_skill_count,
    ROUND(s.preferred_match_rate, 2) AS preferred_match_rate,
    ROUND(s.match_score, 2) AS match_score,
    COALESCE(mr.matched_required_skills, '') AS matched_required_skills,
    COALESCE(xr.missing_required_skills, '') AS missing_required_skills,
    COALESCE(mp.matched_preferred_skills, '') AS matched_preferred_skills,
    COALESCE(xp.missing_preferred_skills, '') AS missing_preferred_skills,
    s.original_url
FROM scored s
         LEFT JOIN matched_required mr ON mr.job_id = s.job_id
         LEFT JOIN missing_required xr ON xr.job_id = s.job_id
         LEFT JOIN matched_preferred mp ON mp.job_id = s.job_id
         LEFT JOIN missing_preferred xp ON xp.job_id = s.job_id
ORDER BY
    s.match_score DESC,
    s.matched_required_skill_count DESC,
    s.required_match_rate DESC,
    s.matched_preferred_skill_count DESC,
    s.job_id DESC
LIMIT 30;
