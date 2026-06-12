CREATE TABLE user_oauth_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    auth_provider VARCHAR(30) NOT NULL,
    encrypted_access_token TEXT NOT NULL,
    token_type VARCHAR(30),
    scopes VARCHAR(500),
    issued_at DATETIME(6),
    expires_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_oauth_tokens_user_provider (user_id, auth_provider),
    KEY idx_user_oauth_tokens_provider (auth_provider),
    CONSTRAINT fk_user_oauth_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
