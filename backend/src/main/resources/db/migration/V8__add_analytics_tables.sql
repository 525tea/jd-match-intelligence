CREATE TABLE skill_trends
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    period_type     VARCHAR(20)    NOT NULL DEFAULT 'MONTHLY',
    period_start    DATE           NOT NULL,
    skill_id        BIGINT         NOT NULL,
    job_count       BIGINT         NOT NULL DEFAULT 0,
    required_count  BIGINT         NOT NULL DEFAULT 0,
    preferred_count BIGINT         NOT NULL DEFAULT 0,
    trend_score     DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    computed_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_trends_period_skill (period_type, period_start, skill_id),
    KEY             idx_skill_trends_skill_period (skill_id, period_type, period_start),
    KEY             idx_skill_trends_period_score (period_type, period_start, trend_score),
    CONSTRAINT fk_skill_trends_skill
        FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE skill_cooccurrence
(
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    period_type          VARCHAR(20)    NOT NULL DEFAULT 'MONTHLY',
    period_start         DATE           NOT NULL,
    base_skill_id        BIGINT         NOT NULL,
    co_skill_id          BIGINT         NOT NULL,
    cooccurrence_count   BIGINT         NOT NULL DEFAULT 0,
    base_skill_job_count BIGINT         NOT NULL DEFAULT 0,
    co_skill_job_count   BIGINT         NOT NULL DEFAULT 0,
    lift_score           DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    computed_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_cooccurrence_period_pair (
        period_type,
        period_start,
        base_skill_id,
        co_skill_id
        ),
    KEY                  idx_skill_cooccurrence_base_period (base_skill_id, period_type, period_start),
    KEY                  idx_skill_cooccurrence_co_period (co_skill_id, period_type, period_start),
    KEY                  idx_skill_cooccurrence_period_lift (period_type, period_start, lift_score),
    CONSTRAINT fk_skill_cooccurrence_base_skill
        FOREIGN KEY (base_skill_id) REFERENCES skills (id),
    CONSTRAINT fk_skill_cooccurrence_co_skill
        FOREIGN KEY (co_skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_market_stats
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    period_type              VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY',
    period_start             DATE         NOT NULL,
    role                     VARCHAR(50)  NOT NULL,
    career_level             VARCHAR(30)  NOT NULL,
    location_region          VARCHAR(100) NOT NULL DEFAULT 'ALL',
    remote_type              VARCHAR(30)  NOT NULL DEFAULT 'ALL',
    job_count                BIGINT       NOT NULL DEFAULT 0,
    open_job_count           BIGINT       NOT NULL DEFAULT 0,
    closed_job_count         BIGINT       NOT NULL DEFAULT 0,
    expired_job_count        BIGINT       NOT NULL DEFAULT 0,
    avg_min_experience_years DECIMAL(5, 2),
    avg_max_experience_years DECIMAL(5, 2),
    computed_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_market_stats_dimension (
        period_type,
        period_start,
        role,
        career_level,
        location_region,
        remote_type
        ),
    KEY                      idx_job_market_stats_period_role (period_type, period_start, role),
    KEY                      idx_job_market_stats_period_count (period_type, period_start, job_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE skill_experience_market
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    period_type     VARCHAR(20)    NOT NULL DEFAULT 'MONTHLY',
    period_start    DATE           NOT NULL,
    skill_id        BIGINT         NOT NULL,
    tag_code        VARCHAR(50)    NOT NULL,
    job_count       BIGINT         NOT NULL DEFAULT 0,
    skill_job_count BIGINT         NOT NULL DEFAULT 0,
    tag_job_count   BIGINT         NOT NULL DEFAULT 0,
    lift_score      DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    computed_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_experience_market_period_pair (
        period_type,
        period_start,
        skill_id,
        tag_code
        ),
    KEY             idx_skill_experience_market_skill_period (skill_id, period_type, period_start),
    KEY             idx_skill_experience_market_tag_period (tag_code, period_type, period_start),
    KEY             idx_skill_experience_market_period_lift (period_type, period_start, lift_score),
    CONSTRAINT fk_skill_experience_market_skill
        FOREIGN KEY (skill_id) REFERENCES skills (id),
    CONSTRAINT fk_skill_experience_market_tag
        FOREIGN KEY (tag_code) REFERENCES experience_tag_codes (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
