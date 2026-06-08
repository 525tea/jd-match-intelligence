SET @target_month = DATE_FORMAT(CURRENT_DATE(), '%Y-%m-01');

SELECT
    st.period_type,
    st.period_start,
    s.name AS skill_name,
    st.job_count,
    st.required_count,
    st.preferred_count,
    st.trend_score
FROM skill_trends st
         JOIN skills s ON s.id = st.skill_id
WHERE st.period_type = 'MONTHLY'
  AND st.period_start = @target_month
ORDER BY st.trend_score DESC, st.job_count DESC, s.name ASC
    LIMIT 20;

SELECT
    sc.period_type,
    sc.period_start,
    base.name AS base_skill,
    co.name AS co_skill,
    sc.cooccurrence_count,
    sc.base_skill_job_count,
    sc.co_skill_job_count,
    sc.lift_score
FROM skill_cooccurrence sc
         JOIN skills base ON base.id = sc.base_skill_id
         JOIN skills co ON co.id = sc.co_skill_id
WHERE sc.period_type = 'MONTHLY'
  AND sc.period_start = @target_month
ORDER BY sc.lift_score DESC, sc.cooccurrence_count DESC, base.name ASC, co.name ASC
    LIMIT 20;

SELECT
    sem.period_type,
    sem.period_start,
    s.name AS skill_name,
    etc.name AS tag_name,
    sem.job_count,
    sem.skill_job_count,
    sem.tag_job_count,
    sem.lift_score
FROM skill_experience_market sem
         JOIN skills s ON s.id = sem.skill_id
         JOIN experience_tag_codes etc ON etc.code = sem.tag_code
WHERE sem.period_type = 'MONTHLY'
  AND sem.period_start = @target_month
ORDER BY sem.lift_score DESC, sem.job_count DESC, s.name ASC, etc.name ASC
    LIMIT 20;
