-- gap analysis smoke fixture
-- 목적:
-- - /gap-analysis/projects/{userProjectId} API를 실제 DB 데이터로 검증할 수 있게 smoke 전용 project analysis를 만든다.
-- - gap-smoke@example.com smoke 전용 사용자를 함께 만든다.
-- - smoke 사용자 비밀번호는 Jobflow-gap-smoke-123! 이다.
-- - 이 fixture는 smoke 전용 user_project external_id만 upsert한다.

SET @gap_smoke_email := 'gap-smoke@example.com';
SET @gap_smoke_password_hash := '$2a$10$HHAwSJEeNZkHEFPiEG49hOgkXbWuFB2oBbR.EvpCZoIRAUurSmVYq';
SET @gap_smoke_project_external_id := 'gap-analysis-smoke-project';
SET @gap_smoke_source_hash := 'gap_analysis_smoke_source_hash_00000000000000000000000000000000';

INSERT INTO users (
    email,
    password_hash,
    name,
    role,
    auth_provider,
    provider_id
)
VALUES (
    @gap_smoke_email,
    @gap_smoke_password_hash,
    'Gap Smoke',
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
    @gap_smoke_project_external_id,
    'Gap Analysis Smoke Project',
    'https://github.com/jobflow/gap-analysis-smoke',
    'Java Spring backend project used for gap analysis smoke verification'
FROM users u
WHERE u.email = @gap_smoke_email
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    repository_url = VALUES(repository_url),
    description = VALUES(description),
    updated_at = CURRENT_TIMESTAMP(6);

SET @gap_smoke_project_id := (
    SELECT up.id
    FROM user_projects up
             JOIN users u ON u.id = up.user_id
    WHERE u.email = @gap_smoke_email
      AND up.source_type = 'GITHUB'
      AND up.external_id = @gap_smoke_project_external_id
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
    @gap_smoke_project_id,
    1,
    @gap_smoke_source_hash,
    'gap-smoke',
    'fixture-v1',
    JSON_OBJECT(
        'source', 'gap-analysis-smoke',
        'skills', JSON_ARRAY('Java', 'Spring Boot', 'Spring Framework', 'MySQL', 'Redis', 'Docker', 'AWS', 'Git')
    ),
    0.9900,
    CURRENT_TIMESTAMP(6)
WHERE @gap_smoke_project_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    source_hash = VALUES(source_hash),
    commit_sha = VALUES(commit_sha),
    model_version = VALUES(model_version),
    raw_analysis = VALUES(raw_analysis),
    confidence_score = VALUES(confidence_score),
    analyzed_at = VALUES(analyzed_at);

SET @gap_smoke_analysis_id := (
    SELECT upa.id
    FROM user_project_analysis upa
    WHERE upa.user_project_id = @gap_smoke_project_id
      AND upa.analysis_version = 1
    LIMIT 1
);

DELETE FROM user_project_skills
WHERE analysis_id = @gap_smoke_analysis_id;

INSERT INTO user_project_skills (
    analysis_id,
    skill_id,
    confidence,
    evidence,
    source
)
SELECT
    @gap_smoke_analysis_id,
    s.id,
    0.9500,
    CONCAT('gap analysis smoke fixture: ', s.name),
    'STATIC'
FROM skills s
WHERE @gap_smoke_analysis_id IS NOT NULL
  AND s.name IN (
      'Java',
      'Spring Boot',
      'Spring Framework',
      'MySQL',
      'Redis',
      'Docker',
      'AWS',
      'Git'
  );

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
WHERE u.email = @gap_smoke_email
  AND up.source_type = 'GITHUB'
  AND up.external_id = @gap_smoke_project_external_id
  AND upa.analysis_version = 1
GROUP BY
    u.id,
    u.email,
    up.id,
    up.external_id,
    upa.id;
