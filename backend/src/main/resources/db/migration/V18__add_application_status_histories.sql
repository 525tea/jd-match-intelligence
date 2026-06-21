CREATE TABLE application_status_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    previous_status VARCHAR(30),
    next_status VARCHAR(30) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_application_status_histories_application_changed_at (application_id, changed_at),
    CONSTRAINT fk_application_status_histories_application
        FOREIGN KEY (application_id) REFERENCES applications (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
