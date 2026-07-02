ALTER TABLE outbox_events
    ADD COLUMN schema_version INT NOT NULL DEFAULT 1 AFTER id;
