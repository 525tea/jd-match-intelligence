CREATE TABLE IF NOT EXISTS jd_phrase_tag_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT,
    phrase VARCHAR(200) NOT NULL,
    normalized_phrase VARCHAR(200) NOT NULL,
    tag_code VARCHAR(50) NOT NULL,
    confidence DECIMAL(5, 4) NOT NULL DEFAULT 0.8000,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_jd_phrase_tag_mapping_phrase_tag (normalized_phrase, tag_code),
    KEY idx_jd_phrase_tag_mapping_tag (tag_code),
    KEY idx_jd_phrase_tag_mapping_enabled (enabled),
    CONSTRAINT fk_jd_phrase_tag_mapping_tag
    FOREIGN KEY (tag_code) REFERENCES experience_tag_codes (code)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO experience_tag_codes (code, name, description)
VALUES
    ('HIGH_TRAFFIC', '대용량 트래픽', '대용량 트래픽 처리, 트래픽 급증 대응, 고부하 서비스 운영 경험'),
    ('SCALABILITY', '확장성', '서비스 확장, 수평 확장, 분산 구조 설계 및 확장 가능한 아키텍처 경험'),
    ('PERFORMANCE', '성능 최적화', '쿼리 튜닝, 캐싱, 병목 제거, 응답 속도 개선 등 성능 개선 경험'),
    ('RELIABILITY', '안정성', '장애 대응, 재시도, 장애 격리, 고가용성, 복구 전략 등 안정성 개선 경험'),
    ('TESTING', '테스트', '단위 테스트, 통합 테스트, 테스트 자동화, 회귀 테스트 기반 품질 관리 경험'),
    ('CI_CD', 'CI/CD', '빌드, 테스트, 배포 자동화 및 CI/CD 파이프라인 구성 경험'),
    ('MONITORING', '모니터링', '로그, 메트릭, 알림, APM 등을 활용한 운영 관측 및 문제 추적 경험'),
    ('CLOUD_INFRA', '클라우드/인프라', '클라우드, 컨테이너, 인프라 구성, 배포 환경 운영 경험'),
    ('SECURITY', '보안', '인증, 인가, 토큰, 권한, 보안 취약점 대응 등 보안 관련 경험')
    ON DUPLICATE KEY UPDATE code = code;

INSERT INTO skills (name, normalized_name, category)
VALUES
    ('Java', 'java', 'LANGUAGE'),
    ('Kotlin', 'kotlin', 'LANGUAGE'),
    ('JavaScript', 'javascript', 'LANGUAGE'),
    ('TypeScript', 'typescript', 'LANGUAGE'),
    ('Python', 'python', 'LANGUAGE'),
    ('SQL', 'sql', 'LANGUAGE'),

    ('Spring Boot', 'spring boot', 'FRAMEWORK'),
    ('Spring Framework', 'spring framework', 'FRAMEWORK'),
    ('Spring Security', 'spring security', 'FRAMEWORK'),
    ('Spring Data JPA', 'spring data jpa', 'FRAMEWORK'),
    ('Hibernate', 'hibernate', 'FRAMEWORK'),
    ('JUnit', 'junit', 'FRAMEWORK'),
    ('Mockito', 'mockito', 'FRAMEWORK'),
    ('React', 'react', 'FRAMEWORK'),
    ('Next.js', 'next.js', 'FRAMEWORK'),

    ('MySQL', 'mysql', 'DATABASE'),
    ('PostgreSQL', 'postgresql', 'DATABASE'),
    ('Redis', 'redis', 'DATABASE'),
    ('Elasticsearch', 'elasticsearch', 'DATABASE'),
    ('H2', 'h2', 'DATABASE'),

    ('Docker', 'docker', 'INFRA'),
    ('Docker Compose', 'docker compose', 'INFRA'),
    ('Kubernetes', 'kubernetes', 'INFRA'),
    ('Nginx', 'nginx', 'INFRA'),
    ('Linux', 'linux', 'INFRA'),

    ('AWS', 'aws', 'CLOUD'),
    ('Amazon EC2', 'amazon ec2', 'CLOUD'),
    ('Amazon RDS', 'amazon rds', 'CLOUD'),
    ('Amazon S3', 'amazon s3', 'CLOUD'),

    ('Git', 'git', 'TOOL'),
    ('GitHub', 'github', 'TOOL'),
    ('GitHub Actions', 'github actions', 'TOOL'),
    ('Gradle', 'gradle', 'TOOL'),
    ('Maven', 'maven', 'TOOL'),
    ('Flyway', 'flyway', 'TOOL'),
    ('Postman', 'postman', 'TOOL'),

    ('REST API', 'rest api', 'METHODOLOGY'),
    ('OAuth2', 'oauth2', 'METHODOLOGY'),
    ('JWT', 'jwt', 'METHODOLOGY'),
    ('TDD', 'tdd', 'METHODOLOGY'),
    ('DDD', 'ddd', 'METHODOLOGY'),
    ('MSA', 'msa', 'METHODOLOGY'),
    ('CQRS', 'cqrs', 'METHODOLOGY'),
    ('Outbox Pattern', 'outbox pattern', 'METHODOLOGY'),

    ('Node.js', 'node.js', 'FRAMEWORK'),
    ('Express', 'express', 'FRAMEWORK'),
    ('NestJS', 'nestjs', 'FRAMEWORK'),
    ('FastAPI', 'fastapi', 'FRAMEWORK'),
    ('Django', 'django', 'FRAMEWORK'),
    ('Flask', 'flask', 'FRAMEWORK'),
    ('Vue.js', 'vue.js', 'FRAMEWORK'),
    ('Angular', 'angular', 'FRAMEWORK'),
    ('Tailwind CSS', 'tailwind css', 'FRAMEWORK'),

    ('QueryDSL', 'querydsl', 'FRAMEWORK'),
    ('MyBatis', 'mybatis', 'FRAMEWORK'),

    ('MariaDB', 'mariadb', 'DATABASE'),
    ('Oracle Database', 'oracle database', 'DATABASE'),
    ('MongoDB', 'mongodb', 'DATABASE'),

    ('Kafka', 'kafka', 'INFRA'),
    ('RabbitMQ', 'rabbitmq', 'INFRA'),
    ('Jenkins', 'jenkins', 'INFRA'),
    ('GitLab CI', 'gitlab ci', 'INFRA'),
    ('Terraform', 'terraform', 'INFRA'),
    ('Helm', 'helm', 'INFRA'),
    ('Argo CD', 'argo cd', 'INFRA'),

    ('Amazon ECS', 'amazon ecs', 'CLOUD'),
    ('Amazon EKS', 'amazon eks', 'CLOUD'),
    ('AWS Lambda', 'aws lambda', 'CLOUD'),
    ('Amazon CloudWatch', 'amazon cloudwatch', 'CLOUD'),
    ('Google Cloud Platform', 'google cloud platform', 'CLOUD'),
    ('Microsoft Azure', 'microsoft azure', 'CLOUD'),

    ('Swagger', 'swagger', 'TOOL'),
    ('OpenAPI', 'openapi', 'TOOL'),
    ('Testcontainers', 'testcontainers', 'TOOL'),
    ('REST Assured', 'rest assured', 'TOOL'),
    ('k6', 'k6', 'TOOL'),
    ('JMeter', 'jmeter', 'TOOL'),
    ('Prometheus', 'prometheus', 'TOOL'),
    ('Grafana', 'grafana', 'TOOL'),
    ('Loki', 'loki', 'TOOL'),
    ('Sentry', 'sentry', 'TOOL'),

    ('Event Driven Architecture', 'event driven architecture', 'METHODOLOGY'),
    ('Clean Architecture', 'clean architecture', 'METHODOLOGY'),
    ('Hexagonal Architecture', 'hexagonal architecture', 'METHODOLOGY'),
    ('Circuit Breaker', 'circuit breaker', 'METHODOLOGY'),
    ('Rate Limiting', 'rate limiting', 'METHODOLOGY')
    ON DUPLICATE KEY UPDATE name = name;

INSERT INTO jd_phrase_tag_mapping (phrase, normalized_phrase, tag_code, confidence)
VALUES
    ('대용량 트래픽', '대용량 트래픽', 'HIGH_TRAFFIC', 0.9500),
    ('대규모 트래픽', '대규모 트래픽', 'HIGH_TRAFFIC', 0.9500),
    ('트래픽 급증', '트래픽 급증', 'HIGH_TRAFFIC', 0.9000),
    ('고부하 서비스', '고부하 서비스', 'HIGH_TRAFFIC', 0.9000),

    ('확장 가능한 아키텍처', '확장 가능한 아키텍처', 'SCALABILITY', 0.9500),
    ('수평 확장', '수평 확장', 'SCALABILITY', 0.9000),
    ('분산 시스템', '분산 시스템', 'SCALABILITY', 0.8500),
    ('마이크로서비스', '마이크로서비스', 'SCALABILITY', 0.8000),

    ('성능 최적화', '성능 최적화', 'PERFORMANCE', 0.9500),
    ('쿼리 튜닝', '쿼리 튜닝', 'PERFORMANCE', 0.9000),
    ('병목 개선', '병목 개선', 'PERFORMANCE', 0.9000),
    ('응답 속도 개선', '응답 속도 개선', 'PERFORMANCE', 0.8500),
    ('캐싱 전략', '캐싱 전략', 'PERFORMANCE', 0.8500),

    ('장애 대응', '장애 대응', 'RELIABILITY', 0.9500),
    ('고가용성', '고가용성', 'RELIABILITY', 0.9000),
    ('재시도 처리', '재시도 처리', 'RELIABILITY', 0.8500),
    ('장애 복구', '장애 복구', 'RELIABILITY', 0.8500),

    ('단위 테스트', '단위 테스트', 'TESTING', 0.9500),
    ('통합 테스트', '통합 테스트', 'TESTING', 0.9500),
    ('테스트 자동화', '테스트 자동화', 'TESTING', 0.9000),
    ('회귀 테스트', '회귀 테스트', 'TESTING', 0.8500),

    ('CI/CD', 'ci/cd', 'CI_CD', 0.9500),
    ('CI CD', 'ci cd', 'CI_CD', 0.9500),
    ('배포 자동화', '배포 자동화', 'CI_CD', 0.9000),
    ('빌드 자동화', '빌드 자동화', 'CI_CD', 0.8500),
    ('GitHub Actions', 'github actions', 'CI_CD', 0.8000),

    ('모니터링', '모니터링', 'MONITORING', 0.9500),
    ('로그 수집', '로그 수집', 'MONITORING', 0.8500),
    ('메트릭', '메트릭', 'MONITORING', 0.8500),
    ('알림 시스템', '알림 시스템', 'MONITORING', 0.8000),
    ('APM', 'apm', 'MONITORING', 0.8000),

    ('클라우드 환경', '클라우드 환경', 'CLOUD_INFRA', 0.9000),
    ('인프라 구성', '인프라 구성', 'CLOUD_INFRA', 0.9000),
    ('Docker', 'docker', 'CLOUD_INFRA', 0.8500),
    ('Kubernetes', 'kubernetes', 'CLOUD_INFRA', 0.8500),
    ('AWS', 'aws', 'CLOUD_INFRA', 0.8500),

    ('인증 인가', '인증 인가', 'SECURITY', 0.9500),
    ('인증/인가', '인증/인가', 'SECURITY', 0.9500),
    ('JWT', 'jwt', 'SECURITY', 0.8500),
    ('OAuth2', 'oauth2', 'SECURITY', 0.8500),
    ('보안 취약점', '보안 취약점', 'SECURITY', 0.8500)
    ON DUPLICATE KEY UPDATE phrase = phrase;
