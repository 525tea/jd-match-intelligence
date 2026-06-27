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
    WHERE source = 'perf_fixture'
);

CALL assert_perf_condition(
    @perf_job_count >= @minimum_job_count,
    CONCAT('Expected at least ', @minimum_job_count, ' perf_fixture jobs, actual=', @perf_job_count)
);

SET @skill_link_count = (
    SELECT COUNT(*)
    FROM job_skills js
    JOIN jobs j ON j.id = js.job_id
    WHERE j.source = 'perf_fixture'
);

CALL assert_perf_condition(
    @skill_link_count >= @minimum_job_count,
    CONCAT('Expected at least ', @minimum_job_count, ' perf_fixture job skill links, actual=', @skill_link_count)
);

SET @tag_link_count = (
    SELECT COUNT(*)
    FROM job_experience_tags jet
    JOIN jobs j ON j.id = jet.job_id
    WHERE j.source = 'perf_fixture'
);

CALL assert_perf_condition(
    @tag_link_count >= @minimum_job_count,
    CONCAT('Expected at least ', @minimum_job_count, ' perf_fixture experience tag links, actual=', @tag_link_count)
);

-- deadline null 비율 10% 이하 검증
SET @null_deadline_count = (
    SELECT COUNT(*) FROM jobs WHERE source = 'perf_fixture' AND deadline_at IS NULL
);
SET @null_deadline_rate = @null_deadline_count / @perf_job_count;

CALL assert_perf_condition(
    @null_deadline_rate <= 0.10,
    CONCAT('deadline_at null rate exceeds 10%, actual=', ROUND(@null_deadline_rate * 100, 2), '%')
);

-- role 분포 검증: BACKEND 20% 이상 확인
SET @backend_count = (
    SELECT COUNT(*) FROM jobs WHERE source = 'perf_fixture' AND role = 'BACKEND'
);
SET @backend_rate = @backend_count / @perf_job_count;

CALL assert_perf_condition(
    @backend_rate >= 0.20,
    CONCAT('BACKEND role rate is below 20%, actual=', ROUND(@backend_rate * 100, 2), '%')
);

-- FULLTEXT 검색 동작 확인
SET @fulltext_result_count = (
    SELECT COUNT(*)
    FROM jobs
    WHERE source = 'perf_fixture'
      AND MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
          AGAINST ('백엔드 Spring Boot' IN BOOLEAN MODE)
);

CALL assert_perf_condition(
    @fulltext_result_count > 0,
    CONCAT('FULLTEXT search returned 0 results for 백엔드 Spring Boot, fulltext index may be missing')
);

-- 요약 출력
SELECT
    'PERFORMANCE_DATASET_SUMMARY' AS check_name,
    DATABASE() AS database_name,
    @perf_job_count AS perf_job_count,
    @skill_link_count AS skill_link_count,
    @tag_link_count AS tag_link_count,
    ROUND(@null_deadline_rate * 100, 2) AS null_deadline_rate_pct,
    ROUND(@backend_rate * 100, 2) AS backend_role_rate_pct,
    @fulltext_result_count AS fulltext_search_result_count;

-- role 분포
SELECT
    role,
    COUNT(*) AS job_count,
    ROUND(COUNT(*) * 100.0 / @perf_job_count, 1) AS pct
FROM jobs
WHERE source = 'perf_fixture'
GROUP BY role
ORDER BY job_count DESC;

-- location 분포
SELECT
    location_region,
    COUNT(*) AS job_count,
    ROUND(COUNT(*) * 100.0 / @perf_job_count, 1) AS pct
FROM jobs
WHERE source = 'perf_fixture'
GROUP BY location_region
ORDER BY job_count DESC;

-- career level 분포
SELECT
    career_level,
    COUNT(*) AS job_count
FROM jobs
WHERE source = 'perf_fixture'
GROUP BY career_level
ORDER BY job_count DESC;

-- skill 분포 (상위 10개)
SELECT
    s.name AS skill_name,
    COUNT(*) AS link_count
FROM job_skills js
JOIN jobs j ON j.id = js.job_id
JOIN skills s ON s.id = js.skill_id
WHERE j.source = 'perf_fixture'
GROUP BY s.name
ORDER BY link_count DESC
LIMIT 10;

DROP PROCEDURE IF EXISTS assert_perf_condition;
