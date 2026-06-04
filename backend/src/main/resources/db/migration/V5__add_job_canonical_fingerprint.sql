ALTER TABLE jobs
    ADD COLUMN canonical_fingerprint VARCHAR(128) NULL AFTER external_id;

CREATE INDEX idx_jobs_canonical_fingerprint
    ON jobs (canonical_fingerprint);
