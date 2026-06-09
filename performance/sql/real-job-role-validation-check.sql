SELECT
    COUNT(*) AS labeled_count,
    SUM(CASE WHEN j.id IS NOT NULL THEN 1 ELSE 0 END) AS found_job_count,
    SUM(CASE WHEN j.id IS NOT NULL AND j.role = e.expected_role THEN 1 ELSE 0 END) AS matched_count,
    SUM(CASE WHEN j.id IS NOT NULL AND j.role <> e.expected_role THEN 1 ELSE 0 END) AS mismatched_count,
    ROUND(
            SUM(CASE WHEN j.id IS NOT NULL AND j.role = e.expected_role THEN 1 ELSE 0 END)
                / NULLIF(SUM(CASE WHEN j.id IS NOT NULL THEN 1 ELSE 0 END), 0) * 100,
            2
    ) AS role_accuracy
FROM real_job_role_validation_expected e
         LEFT JOIN jobs j
                   ON j.source COLLATE utf8mb4_unicode_ci = e.source
                       AND j.external_id COLLATE utf8mb4_unicode_ci = e.external_id;

SELECT
    e.source,
    e.external_id,
    e.expected_role,
    j.role AS actual_role,
    j.title,
    j.company_name,
    e.reason
FROM real_job_role_validation_expected e
         JOIN jobs j
              ON j.source COLLATE utf8mb4_unicode_ci = e.source
                  AND j.external_id COLLATE utf8mb4_unicode_ci = e.external_id
WHERE j.role <> e.expected_role
ORDER BY e.source ASC, e.external_id ASC;

SELECT
    e.source,
    e.external_id,
    e.expected_role,
    e.reason
FROM real_job_role_validation_expected e
         LEFT JOIN jobs j
                   ON j.source COLLATE utf8mb4_unicode_ci = e.source
                       AND j.external_id COLLATE utf8mb4_unicode_ci = e.external_id
WHERE j.id IS NULL
ORDER BY e.source ASC, e.external_id ASC;

SELECT
    e.source,
    e.expected_role,
    COUNT(*) AS labeled_count,
    SUM(CASE WHEN j.id IS NOT NULL AND j.role = e.expected_role THEN 1 ELSE 0 END) AS matched_count,
    SUM(CASE WHEN j.id IS NOT NULL AND j.role <> e.expected_role THEN 1 ELSE 0 END) AS mismatched_count
FROM real_job_role_validation_expected e
         LEFT JOIN jobs j
                   ON j.source COLLATE utf8mb4_unicode_ci = e.source
                       AND j.external_id COLLATE utf8mb4_unicode_ci = e.external_id
GROUP BY e.source, e.expected_role
ORDER BY e.source ASC, e.expected_role ASC;
