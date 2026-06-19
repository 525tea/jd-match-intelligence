SET @source = CONVERT('CANONICAL_SMOKE' USING utf8mb4) COLLATE utf8mb4_unicode_ci;
SET @fingerprint = CONVERT('canonical-smoke|backend-engineer|seoul' USING utf8mb4) COLLATE utf8mb4_unicode_ci;

SELECT
    COUNT(*) AS canonical_smoke_job_count,
    COUNT(DISTINCT canonical_fingerprint) AS canonical_group_count,
    SUM(CASE WHEN original_url IS NOT NULL AND original_url <> '' THEN 1 ELSE 0 END) AS company_original_url_count,
    SUM(CASE WHEN status = 'OPEN' THEN 1 ELSE 0 END) AS open_job_count
FROM jobs
WHERE source = @source
  AND canonical_fingerprint = @fingerprint;

SELECT
    id,
    source,
    external_id,
    canonical_fingerprint,
    title,
    company_name,
    url,
    original_url,
    status
FROM jobs
WHERE source = @source
  AND canonical_fingerprint = @fingerprint
ORDER BY id ASC;
