CREATE TABLE notification_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_retry_at DATETIME(6),
    last_attempted_at DATETIME(6),
    sent_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_logs_user_job_type (user_id, job_id, type),
    KEY idx_notification_logs_type_status_retry (type, status, next_retry_at),
    KEY idx_notification_logs_user (user_id),
    KEY idx_notification_logs_job (job_id),
    CONSTRAINT fk_notification_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notification_logs_job
        FOREIGN KEY (job_id) REFERENCES jobs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_log_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_message_id VARCHAR(255),
    failure_reason TEXT,
    attempted_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_attempts_log_attempt (notification_log_id, attempt_number),
    KEY idx_notification_attempts_status_attempted (status, attempted_at),
    CONSTRAINT fk_notification_attempts_log
        FOREIGN KEY (notification_log_id) REFERENCES notification_logs (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
