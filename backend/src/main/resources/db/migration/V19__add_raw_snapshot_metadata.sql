ALTER TABLE jobs
    ADD COLUMN raw_snapshot_key VARCHAR(500),
    ADD COLUMN raw_snapshot_hash CHAR(64),
    ADD COLUMN raw_snapshot_size_bytes BIGINT,
    ADD COLUMN raw_snapshot_storage_type VARCHAR(30),
    ADD COLUMN raw_snapshot_saved_at DATETIME(6);

CREATE INDEX idx_jobs_raw_snapshot_key
    ON jobs (raw_snapshot_key);

CREATE INDEX idx_jobs_raw_snapshot_hash
    ON jobs (raw_snapshot_hash);
