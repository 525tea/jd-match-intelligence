CREATE TABLE skill_aliases (
                               id BIGINT NOT NULL AUTO_INCREMENT,
                               skill_id BIGINT NOT NULL,
                               alias VARCHAR(100) NOT NULL,
                               normalized_alias VARCHAR(100) NOT NULL,
                               confidence DECIMAL(5, 4) NOT NULL DEFAULT 1.0000,
                               enabled BOOLEAN NOT NULL DEFAULT TRUE,
                               created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                               PRIMARY KEY (id),
                               UNIQUE KEY uk_skill_aliases_normalized_alias (normalized_alias),
                               KEY idx_skill_aliases_skill (skill_id),
                               KEY idx_skill_aliases_enabled (enabled),
                               CONSTRAINT fk_skill_aliases_skill
                                   FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'SpringBoot', 'springboot', 0.9500
FROM skills
WHERE normalized_name = 'spring boot'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'spring boot', 'spring boot', 1.0000
FROM skills
WHERE normalized_name = 'spring boot'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '스프링 부트', '스프링 부트', 0.9500
FROM skills
WHERE normalized_name = 'spring boot'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'spring', 'spring', 0.8000
FROM skills
WHERE normalized_name = 'spring boot'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'k8s', 'k8s', 0.9500
FROM skills
WHERE normalized_name = 'kubernetes'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '쿠버네티스', '쿠버네티스', 0.9500
FROM skills
WHERE normalized_name = 'kubernetes'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'js', 'js', 0.9000
FROM skills
WHERE normalized_name = 'javascript'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, 'Java Script', 'java script', 0.9000
FROM skills
WHERE normalized_name = 'javascript'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '타입스크립트', '타입스크립트', 0.9500
FROM skills
WHERE normalized_name = 'typescript'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '도커', '도커', 0.9500
FROM skills
WHERE normalized_name = 'docker'
    ON DUPLICATE KEY UPDATE alias = alias;

INSERT INTO skill_aliases (skill_id, alias, normalized_alias, confidence)
SELECT id, '깃허브 액션', '깃허브 액션', 0.9000
FROM skills
WHERE normalized_name = 'github actions'
    ON DUPLICATE KEY UPDATE alias = alias;
