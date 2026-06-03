CREATE TABLE user_jobs (
                           id BIGINT NOT NULL AUTO_INCREMENT,
                           user_id BIGINT NOT NULL,
                           job_id BIGINT NOT NULL,
                           status VARCHAR(30) NOT NULL DEFAULT 'VIEWED',
                           viewed_at DATETIME(6),
                           saved_at DATETIME(6),
                           ignored_at DATETIME(6),
                           created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                           updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                           PRIMARY KEY (id),
                           UNIQUE KEY uk_user_jobs_user_job (user_id, job_id),
                           KEY idx_user_jobs_user_status (user_id, status),
                           KEY idx_user_jobs_job_status (job_id, status),
                           CONSTRAINT fk_user_jobs_user
                               FOREIGN KEY (user_id) REFERENCES users (id),
                           CONSTRAINT fk_user_jobs_job
                               FOREIGN KEY (job_id) REFERENCES jobs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
