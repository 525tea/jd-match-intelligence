ALTER TABLE jobs
    ADD COLUMN original_url VARCHAR(1000),
    ADD COLUMN collected_at DATETIME(6),
    ADD COLUMN last_seen_at DATETIME(6),
    ADD COLUMN source_updated_at DATETIME(6),
    ADD COLUMN raw_data JSON,
    ADD COLUMN crawler_version VARCHAR(50);

CREATE INDEX idx_jobs_source_last_seen
    ON jobs (source, last_seen_at);

CREATE INDEX idx_jobs_source_status
    ON jobs (source, status);
