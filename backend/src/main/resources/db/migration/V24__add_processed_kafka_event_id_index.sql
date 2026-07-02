ALTER TABLE processed_kafka_events
    ADD KEY idx_processed_kafka_events_event_id (event_id);
