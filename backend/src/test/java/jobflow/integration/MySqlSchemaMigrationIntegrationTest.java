package jobflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jobflow.support.MySqlTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class MySqlSchemaMigrationIntegrationTest extends MySqlTestContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesMysqlMigrationsSuccessfully() {
        Integer failedMigrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0",
                Integer.class
        );
        Integer batchMigrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '17' AND success = 1",
                Integer.class
        );

        assertThat(failedMigrationCount).isZero();
        assertThat(batchMigrationCount).isOne();
    }

    @Test
    void mysqlSchemaContainsCoreTablesAndBatchMetadataTables() {
        List<String> expectedTables = List.of(
                "users",
                "jobs",
                "skills",
                "job_skills",
                "job_experience_tags",
                "outbox_events",
                "BATCH_JOB_INSTANCE",
                "BATCH_JOB_EXECUTION",
                "BATCH_JOB_EXECUTION_PARAMS",
                "BATCH_STEP_EXECUTION",
                "BATCH_STEP_EXECUTION_CONTEXT",
                "BATCH_JOB_EXECUTION_CONTEXT",
                "BATCH_STEP_EXECUTION_SEQ",
                "BATCH_JOB_EXECUTION_SEQ",
                "BATCH_JOB_SEQ"
        );

        for (String tableName : expectedTables) {
            assertThat(tableExists(tableName))
                    .as("table should exist: %s", tableName)
                    .isTrue();
        }
    }

    @Test
    void mysqlFullTextIndexExistsAndCanSearchJobContent() {
        Integer fullTextColumnCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'jobs'
                  AND index_name = 'ft_jobs_search'
                  AND index_type = 'FULLTEXT'
                """,
                Integer.class
        );

        assertThat(fullTextColumnCount).isGreaterThanOrEqualTo(1);

        jdbcTemplate.update(
                """
                INSERT INTO jobs (
                    source,
                    external_id,
                    title,
                    company_name,
                    description,
                    url,
                    role,
                    role_detail,
                    career_level,
                    employment_type,
                    location_country,
                    location_region,
                    location_city,
                    remote_type,
                    status
                )
                VALUES (
                    'TESTCONTAINERS',
                    'fulltext-001',
                    'Spring Backend Engineer',
                    'Example Company',
                    'Spring Boot MySQL fulltext search integration test job',
                    'https://example.com/jobs/fulltext-001',
                    'BACKEND',
                    'Backend',
                    'JUNIOR',
                    'FULL_TIME',
                    'KR',
                    'Seoul',
                    'Gangnam',
                    'ONSITE',
                    'OPEN'
                )
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    description = VALUES(description)
                """
        );

        Integer matchedJobCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM jobs
                WHERE MATCH (
                    title,
                    company_name,
                    description,
                    role_detail,
                    industry,
                    location_region,
                    location_city
                ) AGAINST (? IN BOOLEAN MODE)
                """,
                Integer.class,
                "+spring +backend"
        );

        assertThat(matchedJobCount).isOne();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );

        return count != null && count > 0;
    }
}
