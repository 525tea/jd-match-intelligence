package jobflow.collector.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jobflow.collector.support.MySqlSchemaContractTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CollectorSharedSchemaContractIntegrationTest extends MySqlSchemaContractTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void collectorJpaMappingsValidateAgainstBackendOwnedMysqlSchema() {
        assertThat(tableExists("jobs")).isTrue();
        assertThat(tableExists("skills")).isTrue();
        assertThat(tableExists("skill_aliases")).isTrue();
        assertThat(tableExists("job_skills")).isTrue();
        assertThat(tableExists("job_experience_tags")).isTrue();
        assertThat(tableExists("experience_tag_codes")).isTrue();
        assertThat(tableExists("jd_phrase_tag_mapping")).isTrue();
        assertThat(tableExists("outbox_events")).isTrue();
        assertThat(tableExists("normalization_candidates")).isTrue();
    }

    @Test
    void sharedCollectorTablesKeepRequiredColumns() {
        assertColumns("jobs", List.of(
                "id",
                "source",
                "external_id",
                "canonical_fingerprint",
                "title",
                "company_name",
                "description",
                "url",
                "original_url",
                "role",
                "role_detail",
                "career_level",
                "min_experience_years",
                "max_experience_years",
                "education_level",
                "employment_type",
                "company_size",
                "industry",
                "location_country",
                "location_region",
                "location_city",
                "remote_type",
                "salary_min",
                "salary_max",
                "salary_currency",
                "salary_visible",
                "hiring_count",
                "opened_at",
                "deadline_at",
                "collected_at",
                "last_seen_at",
                "source_updated_at",
                "raw_data",
                "crawler_version",
                "status",
                "created_at",
                "updated_at"
        ));

        assertColumns("skills", List.of(
                "id",
                "name",
                "normalized_name",
                "category",
                "created_at"
        ));

        assertColumns("skill_aliases", List.of(
                "id",
                "skill_id",
                "alias",
                "normalized_alias",
                "confidence",
                "enabled",
                "created_at"
        ));

        assertColumns("job_skills", List.of(
                "id",
                "job_id",
                "skill_id",
                "requirement_type"
        ));

        assertColumns("job_experience_tags", List.of(
                "id",
                "job_id",
                "tag_code",
                "source_phrase"
        ));

        assertColumns("outbox_events", List.of(
                "id",
                "aggregate_type",
                "aggregate_id",
                "event_type",
                "payload",
                "topic",
                "status",
                "retry_count",
                "last_error",
                "created_at",
                "published_at"
        ));

        assertColumns("normalization_candidates", List.of(
                "id",
                "candidate_type",
                "source",
                "value",
                "normalized_value",
                "occurrence_count",
                "first_seen_job_id",
                "last_seen_job_id",
                "sample_job_id",
                "sample_job_title",
                "sample_context",
                "status",
                "created_at",
                "updated_at"
        ));
    }

    @Test
    void sharedCollectorTablesKeepRequiredIndexes() {
        assertThat(indexExists("jobs", "uk_jobs_source_external_id")).isTrue();
        assertThat(indexExists("jobs", "idx_jobs_canonical_fingerprint")).isTrue();
        assertThat(indexExists("jobs", "ft_jobs_search")).isTrue();

        assertThat(indexExists("job_skills", "uk_job_skill_requirement")).isTrue();
        assertThat(indexExists("job_experience_tags", "uk_job_experience_tag")).isTrue();
        assertThat(indexExists("skill_aliases", "uk_skill_aliases_normalized_alias")).isTrue();
        assertThat(indexExists("normalization_candidates", "uk_normalization_candidates_type_source_value")).isTrue();
    }

    private void assertColumns(String tableName, List<String> expectedColumns) {
        for (String columnName : expectedColumns) {
            assertThat(columnExists(tableName, columnName))
                    .as("column should exist: %s.%s", tableName, columnName)
                    .isTrue();
        }
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

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );

        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """,
                Integer.class,
                tableName,
                indexName
        );

        return count != null && count > 0;
    }
}
