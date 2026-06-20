-- Skill alias / JD section label candidate review.
-- Run in IntelliJ DB Console after collect-normalization-candidates.sh.

SELECT
    candidate_type,
    source,
    status,
    COUNT(*) AS candidate_count,
    SUM(occurrence_count) AS occurrence_sum
FROM normalization_candidates
GROUP BY candidate_type, source, status
ORDER BY candidate_type ASC, source ASC, status ASC;

SELECT
    candidate_type,
    source,
    value,
    normalized_value,
    occurrence_count,
    sample_job_id,
    sample_job_title,
    sample_context
FROM normalization_candidates
WHERE status = 'PENDING'
ORDER BY occurrence_count DESC, candidate_type ASC, source ASC, normalized_value ASC
LIMIT 50;

SELECT
    source,
    value,
    normalized_value,
    occurrence_count,
    sample_job_id,
    sample_job_title,
    sample_context
FROM normalization_candidates
WHERE candidate_type = 'SKILL_ALIAS'
  AND status = 'PENDING'
ORDER BY occurrence_count DESC, normalized_value ASC
LIMIT 30;

SELECT
    source,
    value,
    normalized_value,
    occurrence_count,
    sample_job_id,
    sample_job_title,
    sample_context
FROM normalization_candidates
WHERE candidate_type = 'JD_SECTION_LABEL'
  AND status = 'PENDING'
ORDER BY occurrence_count DESC, normalized_value ASC
LIMIT 30;
