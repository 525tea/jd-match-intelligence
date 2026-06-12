CREATE TABLE job_skill_index (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 job_id BIGINT NOT NULL,
                                 skill_id BIGINT NOT NULL,
                                 requirement_type VARCHAR(30) NOT NULL,
                                 source VARCHAR(50) NOT NULL,
                                 job_status VARCHAR(30) NOT NULL,
                                 role VARCHAR(50) NOT NULL,
                                 career_level VARCHAR(30) NOT NULL,
                                 location_region VARCHAR(100),
                                 location_city VARCHAR(100),
                                 remote_type VARCHAR(30) NOT NULL,
                                 deadline_at DATETIME(6),
                                 computed_at DATETIME(6) NOT NULL,

                                 UNIQUE KEY uk_job_skill_index_job_skill_requirement (job_id, skill_id, requirement_type),
                                 KEY idx_job_skill_index_skill_requirement (skill_id, requirement_type),
                                 KEY idx_job_skill_index_job (job_id),
                                 KEY idx_job_skill_index_role_career (role, career_level),
                                 KEY idx_job_skill_index_location (location_region, location_city),
                                 KEY idx_job_skill_index_deadline (deadline_at),

                                 CONSTRAINT fk_job_skill_index_job
                                     FOREIGN KEY (job_id) REFERENCES jobs (id)
                                         ON DELETE CASCADE,
                                 CONSTRAINT fk_job_skill_index_skill
                                     FOREIGN KEY (skill_id) REFERENCES skills (id)
);
