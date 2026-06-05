SELECT
    id,
    external_id,
    title,
    MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
    AGAINST ('k8s' IN NATURAL LANGUAGE MODE) AS score
FROM jobs
WHERE status = 'OPEN'
  AND MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
    AGAINST ('k8s' IN NATURAL LANGUAGE MODE)
ORDER BY score DESC, created_at DESC;

SELECT
    id,
    external_id,
    title,
    MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
    AGAINST ('Kubernetes' IN NATURAL LANGUAGE MODE) AS score
FROM jobs
WHERE status = 'OPEN'
  AND MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
    AGAINST ('Kubernetes' IN NATURAL LANGUAGE MODE)
ORDER BY score DESC, created_at DESC;

SELECT
    id,
    external_id,
    title,
    MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
    AGAINST ('Spring' IN NATURAL LANGUAGE MODE) AS score
FROM jobs
WHERE status = 'OPEN'
  AND MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
    AGAINST ('Spring' IN NATURAL LANGUAGE MODE)
ORDER BY score DESC, created_at DESC;
