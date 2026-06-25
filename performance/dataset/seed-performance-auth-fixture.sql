SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @performance_user_email := 'frontend-demo@example.com';
SET @performance_user_password_hash := '$2y$10$PL5lbh6wZsYr6VlCf8Sc6OFgbEgMCzIaE7FOD85ZRYy0PW/AEkOB2';
SET @performance_project_external_id := 'performance-k6-project';
SET @performance_source_hash := 'performance_k6_source_hash_000000000000000000000000000000000000';

INSERT INTO users (
    email,
    password_hash,
    name,
    role,
    auth_provider,
    provider_id
)
VALUES (
    @performance_user_email,
    @performance_user_password_hash,
    'Performance User',
    'USER',
    'LOCAL',
    NULL
)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    name = VALUES(name),
    role = VALUES(role),
    auth_provider = VALUES(auth_provider),
    provider_id = VALUES(provider_id),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO user_projects (
    user_id,
    source_type,
    external_id,
    name,
    repository_url,
    description
)
SELECT
    u.id,
    'GITHUB',
    @performance_project_external_id,
    'Performance K6 Project',
    'https://github.com/example-org/performance-k6-project',
    'Synthetic project fixture used only for k6 baseline recommendation and gap analysis tests.'
FROM users u
WHERE u.email = @performance_user_email
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    repository_url = VALUES(repository_url),
    description = VALUES(description),
    updated_at = CURRENT_TIMESTAMP(6);

SET @performance_project_id := (
    SELECT up.id
    FROM user_projects up
             JOIN users u ON u.id = up.user_id
    WHERE u.email = @performance_user_email
      AND up.source_type = 'GITHUB'
      AND up.external_id = @performance_project_external_id
    LIMIT 1
);

INSERT INTO user_project_analysis (
    user_project_id,
    analysis_version,
    source_hash,
    commit_sha,
    model_version,
    raw_analysis,
    confidence_score,
    analyzed_at
)
SELECT
    @performance_project_id,
    1,
    @performance_source_hash,
    'performance-k6',
    'performance-fixture-v1',
    JSON_OBJECT(
        'source', 'performance-k6-fixture',
        'skills', JSON_ARRAY('Java', 'Spring Boot', 'MySQL', 'Redis', 'Elasticsearch', 'Kafka', 'Docker', 'Kubernetes'),
        'experienceTags', JSON_ARRAY('CI_CD', 'CLOUD_INFRA', 'MONITORING', 'PERFORMANCE_OPTIMIZATION')
    ),
    0.9900,
    CURRENT_TIMESTAMP(6)
WHERE @performance_project_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    source_hash = VALUES(source_hash),
    commit_sha = VALUES(commit_sha),
    model_version = VALUES(model_version),
    raw_analysis = VALUES(raw_analysis),
    confidence_score = VALUES(confidence_score),
    analyzed_at = VALUES(analyzed_at);

SET @performance_analysis_id := (
    SELECT upa.id
    FROM user_project_analysis upa
    WHERE upa.user_project_id = @performance_project_id
      AND upa.analysis_version = 1
    LIMIT 1
);

DELETE FROM user_project_skills
WHERE analysis_id = @performance_analysis_id;

INSERT INTO user_project_skills (
    analysis_id,
    skill_id,
    confidence,
    evidence,
    source
)
SELECT
    @performance_analysis_id,
    s.id,
    0.9500,
    CONCAT('performance k6 fixture: ', s.name),
    'STATIC'
FROM skills s
WHERE @performance_analysis_id IS NOT NULL
  AND s.name IN (
      'Java',
      'Spring Boot',
      'MySQL',
      'Redis',
      'Elasticsearch',
      'Kafka',
      'Docker',
      'Kubernetes'
  );

DELETE FROM user_project_experience_tags
WHERE analysis_id = @performance_analysis_id;

INSERT INTO user_project_experience_tags (
    analysis_id,
    tag_code,
    confidence,
    evidence
)
SELECT
    @performance_analysis_id,
    etc.code,
    0.9200,
    CONCAT('performance k6 fixture: ', etc.name)
FROM experience_tag_codes etc
WHERE @performance_analysis_id IS NOT NULL
  AND etc.code IN (
      'CI_CD',
      'CLOUD_INFRA',
      'MONITORING',
      'PERFORMANCE_OPTIMIZATION'
  );

SELECT
    u.email,
    up.id AS user_project_id,
    up.external_id AS project_external_id,
    upa.id AS analysis_id,
    COUNT(DISTINCT ups.id) AS user_project_skill_count,
    COUNT(DISTINCT upet.id) AS user_project_experience_tag_count
FROM users u
         JOIN user_projects up ON up.user_id = u.id
         JOIN user_project_analysis upa ON upa.user_project_id = up.id
         LEFT JOIN user_project_skills ups ON ups.analysis_id = upa.id
         LEFT JOIN user_project_experience_tags upet ON upet.analysis_id = upa.id
WHERE u.email = @performance_user_email
  AND up.source_type = 'GITHUB'
  AND up.external_id = @performance_project_external_id
  AND upa.analysis_version = 1
GROUP BY
    u.email,
    up.id,
    up.external_id,
    upa.id;
