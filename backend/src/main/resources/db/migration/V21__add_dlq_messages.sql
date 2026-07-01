CREATE TABLE dlq_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    schema_version INT NOT NULL,
    source_topic VARCHAR(255) NOT NULL,
    source_partition INT NOT NULL,
    source_offset BIGINT NOT NULL,
    source_key VARCHAR(255),
    envelope JSON NOT NULL,
    status VARCHAR(30) NOT NULL,
    retry_count INT NOT NULL,
    last_error TEXT,
    failed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    retried_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_dlq_messages_source UNIQUE (source_topic, source_partition, source_offset)
);

CREATE INDEX idx_dlq_messages_status_created_at ON dlq_messages (status, created_at);
