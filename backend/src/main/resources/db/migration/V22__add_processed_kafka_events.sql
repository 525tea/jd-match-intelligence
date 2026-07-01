CREATE TABLE processed_kafka_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    consumer_name VARCHAR(100) NOT NULL,
    event_id BIGINT NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_processed_kafka_events_consumer_event UNIQUE (consumer_name, event_id)
);
