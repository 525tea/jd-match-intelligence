CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    auth_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(100),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_provider (auth_provider, provider_id),
    KEY idx_users_provider (auth_provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(50) NOT NULL,
    external_id VARCHAR(100),
    title VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    description LONGTEXT NOT NULL,
    url VARCHAR(500),
    role VARCHAR(50) NOT NULL,
    role_detail VARCHAR(100),
    career_level VARCHAR(30) NOT NULL DEFAULT 'ANY',
    min_experience_years INT UNSIGNED,
    max_experience_years INT UNSIGNED,
    education_level VARCHAR(30),
    employment_type VARCHAR(30) NOT NULL DEFAULT 'FULL_TIME',
    company_size VARCHAR(30),
    industry VARCHAR(100),
    location_country VARCHAR(50) NOT NULL DEFAULT 'KR',
    location_region VARCHAR(100),
    location_city VARCHAR(100),
    remote_type VARCHAR(30) NOT NULL DEFAULT 'ONSITE',
    salary_min INT UNSIGNED,
    salary_max INT UNSIGNED,
    salary_currency CHAR(3) NOT NULL DEFAULT 'KRW',
    salary_visible BOOLEAN NOT NULL DEFAULT FALSE,
    hiring_count INT UNSIGNED,
    opened_at DATETIME(6),
    deadline_at DATETIME(6),
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_jobs_source_external_id (source, external_id),
    KEY idx_jobs_status_deadline (status, deadline_at),
    KEY idx_jobs_role_career (role, career_level),
    KEY idx_jobs_experience_range (min_experience_years, max_experience_years),
    KEY idx_jobs_employment_location (employment_type, location_region),
    KEY idx_jobs_remote_type (remote_type),
    KEY idx_jobs_company_size (company_size),
    KEY idx_jobs_company (company_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_skills_name (name),
    UNIQUE KEY uk_skills_normalized_name (normalized_name),
    KEY idx_skills_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE experience_tag_codes (
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    requirement_type VARCHAR(30) NOT NULL DEFAULT 'REQUIRED',
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_skill_requirement (job_id, skill_id, requirement_type),
    KEY idx_job_skills_skill (skill_id),
    CONSTRAINT fk_job_skills_job
        FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_job_skills_skill
        FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_experience_tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    tag_code VARCHAR(50) NOT NULL,
    source_phrase VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_experience_tag (job_id, tag_code),
    KEY idx_job_experience_tags_tag (tag_code),
    CONSTRAINT fk_job_experience_tags_job
        FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_job_experience_tags_tag
        FOREIGN KEY (tag_code) REFERENCES experience_tag_codes (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    version BIGINT NOT NULL DEFAULT 0,
    applied_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_applications_user_job (user_id, job_id),
    KEY idx_applications_job_status (job_id, status),
    CONSTRAINT fk_applications_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_applications_job
        FOREIGN KEY (job_id) REFERENCES jobs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL DEFAULT 'GITHUB',
    external_id VARCHAR(200),
    name VARCHAR(200) NOT NULL,
    repository_url VARCHAR(500),
    description VARCHAR(1000),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_projects_source_external (user_id, source_type, external_id),
    KEY idx_user_projects_user_source (user_id, source_type),
    CONSTRAINT fk_user_projects_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_project_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_project_id BIGINT NOT NULL,
    analysis_version INT NOT NULL DEFAULT 1,
    source_hash VARCHAR(64) NOT NULL,
    commit_sha VARCHAR(40),
    model_version VARCHAR(100),
    raw_analysis JSON,
    confidence_score DECIMAL(5, 4),
    analyzed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_project_analysis_version (user_project_id, analysis_version),
    KEY idx_user_project_analysis_hash (source_hash),
    CONSTRAINT fk_user_project_analysis_project
        FOREIGN KEY (user_project_id) REFERENCES user_projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_project_skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    analysis_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    confidence DECIMAL(5, 4),
    evidence VARCHAR(500),
    source VARCHAR(30) NOT NULL DEFAULT 'STATIC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_project_skill (analysis_id, skill_id),
    KEY idx_user_project_skills_skill (skill_id),
    CONSTRAINT fk_user_project_skills_analysis
        FOREIGN KEY (analysis_id) REFERENCES user_project_analysis (id),
    CONSTRAINT fk_user_project_skills_skill
        FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_project_experience_tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    analysis_id BIGINT NOT NULL,
    tag_code VARCHAR(50) NOT NULL,
    confidence DECIMAL(5, 4),
    evidence VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_project_experience_tag (analysis_id, tag_code),
    KEY idx_user_project_experience_tags_tag (tag_code),
    CONSTRAINT fk_user_project_experience_tags_analysis
        FOREIGN KEY (analysis_id) REFERENCES user_project_analysis (id),
    CONSTRAINT fk_user_project_experience_tags_tag
        FOREIGN KEY (tag_code) REFERENCES experience_tag_codes (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    topic VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_outbox_status_created (status, created_at),
    KEY idx_outbox_aggregate (aggregate_type, aggregate_id),
    KEY idx_outbox_topic (topic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
