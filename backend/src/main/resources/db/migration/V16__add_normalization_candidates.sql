CREATE TABLE normalization_candidates (
                                          id BIGINT NOT NULL AUTO_INCREMENT,
                                          candidate_type VARCHAR(40) NOT NULL,
                                          source VARCHAR(50) NOT NULL,
                                          value VARCHAR(150) NOT NULL,
                                          normalized_value VARCHAR(150) NOT NULL,
                                          occurrence_count INT NOT NULL DEFAULT 0,
                                          first_seen_job_id BIGINT,
                                          last_seen_job_id BIGINT,
                                          sample_job_id BIGINT,
                                          sample_job_title VARCHAR(255),
                                          sample_context VARCHAR(1000),
                                          status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                                          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                          updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                              ON UPDATE CURRENT_TIMESTAMP(6),
                                          PRIMARY KEY (id),
                                          UNIQUE KEY uk_normalization_candidates_type_source_value
                                              (candidate_type, source, normalized_value),
                                          KEY idx_normalization_candidates_status_type
                                              (status, candidate_type),
                                          KEY idx_normalization_candidates_source_type
                                              (source, candidate_type),
                                          KEY idx_normalization_candidates_occurrence
                                              (occurrence_count DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
