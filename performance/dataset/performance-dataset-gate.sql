DROP PROCEDURE IF EXISTS assert_perf_condition;

DELIMITER //
CREATE PROCEDURE assert_perf_condition(IN condition_value BOOLEAN, IN message_value VARCHAR(255))
BEGIN
    IF NOT condition_value THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = message_value;
END IF;
END//
DELIMITER ;

SET @expected_database = '{{PERF_DB_NAME}}';
SET @minimum_job_count = {{PERF_MIN_JOB_COUNT}};

CALL assert_perf_condition(
    DATABASE() = @expected_database,
    CONCAT('Performance dataset gate must run against ', @expected_database, ', but current database is ', DATABASE())
);

SET @perf_job_count = (
    SELECT COUNT(*)
    FROM jobs
    WHERE external_id LIKE 'perf-job-%'
);

CALL assert_perf_condition(
    @perf_job_count >= @minimum_job_count,
    CONCAT('Expected at least ', @minimum_job_count, ' performance jobs, actual=', @perf_job_count)
);

SET @skill_link_count = (
    SELECT COUNT(*)
    FROM job_skills js
    JOIN jobs j ON j.id = js.job_id
    WHERE j.external_id LIKE 'perf-job-%'
);

CALL assert_perf_condition(
    @skill_link_count >= @minimum_job_count,
    CONCAT('Expected at least ', @minimum_job_count, ' performance job skill links, actual=', @skill_link_count)
);

SET @tag_link_count = (
    SELECT COUNT(*)
    FROM job_experience_tags jet
    JOIN jobs j ON j.id = jet.job_id
    WHERE j.external_id LIKE 'perf-job-%'
);

CALL assert_perf_condition(
    @tag_link_count >= @minimum_job_count,
    CONCAT('Expected at least ', @minimum_job_count, ' performance job experience tag links, actual=', @tag_link_count)
);

SELECT
    'PERFORMANCE_DATASET_SUMMARY' AS check_name,
    DATABASE() AS database_name,
    @perf_job_count AS perf_job_count,
    @skill_link_count AS skill_link_count,
    @tag_link_count AS tag_link_count,
    COUNT(DISTINCT source) AS source_count,
    COUNT(DISTINCT role) AS role_count,
    SUM(status = 'OPEN') AS open_job_count,
    SUM(deadline_at IS NULL) AS null_deadline_count
FROM jobs
WHERE external_id LIKE 'perf-job-%';

SELECT
    source,
    COUNT(*) AS job_count
FROM jobs
WHERE external_id LIKE 'perf-job-%'
GROUP BY source
ORDER BY source;

SELECT
    role,
    COUNT(*) AS job_count
FROM jobs
WHERE external_id LIKE 'perf-job-%'
GROUP BY role
ORDER BY role;

DROP PROCEDURE IF EXISTS assert_perf_condition;
