package jobflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jobflow.support.MySqlTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SharedSchemaContractIntegrationTest extends MySqlTestContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void backendOwnedSchemaContainsCollectorSharedTables() {
        List<String> sharedTables = List.of(
                "jobs",
                "skills",
                "skill_aliases",
                "experience_tag_codes",
                "jd_phrase_tag_mapping",
                "job_skills",
                "job_experience_tags",
                "outbox_events",
                "normalization_candidates"
        );

        for (String tableName : sharedTables) {
            assertThat(tableExists(tableName))
                    .as("shared table should exist: %s", tableName)
                    .isTrue();
        }
    }

    @Test
    void jobsTableKeepsSharedCollectorContract() {
        assertColumn("jobs", "id", "bigint", false);
        assertColumn("jobs", "source", "varchar", false);
        assertColumn("jobs", "external_id", "varchar", true);
        assertColumn("jobs", "canonical_fingerprint", "varchar", true);
        assertColumn("jobs", "title", "varchar", false);
        assertColumn("jobs", "company_name", "varchar", false);
        assertColumn("jobs", "description", "longtext", false);
        assertColumn("jobs", "url", "varchar", true);
        assertColumn("jobs", "original_url", "varchar", true);
        assertColumn("jobs", "role", "varchar", false);
        assertColumn("jobs", "career_level", "varchar", false);
        assertColumn("jobs", "employment_type", "varchar", false);
        assertColumn("jobs", "location_country", "varchar", false);
        assertColumn("jobs", "location_region", "varchar", true);
        assertColumn("jobs", "location_city", "varchar", true);
        assertColumn("jobs", "remote_type", "varchar", false);
        assertColumn("jobs", "salary_visible", "tinyint", false);
        assertColumn("jobs", "opened_at", "datetime", true);
        assertColumn("jobs", "deadline_at", "datetime", true);
        assertColumn("jobs", "collected_at", "datetime", true);
        assertColumn("jobs", "last_seen_at", "datetime", true);
        assertColumn("jobs", "source_updated_at", "datetime", true);
        assertColumn("jobs", "raw_data", "json", true);
        assertColumn("jobs", "crawler_version", "varchar", true);
        assertColumn("jobs", "status", "varchar", false);
        assertColumn("jobs", "created_at", "datetime", false);
        assertColumn("jobs", "updated_at", "datetime", false);
    }

    @Test
    void skillAndExperienceTablesKeepSharedCollectorContract() {
        assertColumn("skills", "id", "bigint", false);
        assertColumn("skills", "name", "varchar", false);
        assertColumn("skills", "normalized_name", "varchar", false);
        assertColumn("skills", "category", "varchar", false);
        assertColumn("skills", "created_at", "datetime", false);

        assertColumn("skill_aliases", "id", "bigint", false);
        assertColumn("skill_aliases", "skill_id", "bigint", false);
        assertColumn("skill_aliases", "alias", "varchar", false);
        assertColumn("skill_aliases", "normalized_alias", "varchar", false);
        assertColumn("skill_aliases", "confidence", "decimal", false);
        assertColumn("skill_aliases", "enabled", "tinyint", false);
        assertColumn("skill_aliases", "created_at", "datetime", false);

        assertColumn("experience_tag_codes", "code", "varchar", false);
        assertColumn("experience_tag_codes", "name", "varchar", false);
        assertColumn("experience_tag_codes", "description", "varchar", true);
        assertColumn("experience_tag_codes", "created_at", "datetime", false);

        assertColumn("jd_phrase_tag_mapping", "id", "bigint", false);
        assertColumn("jd_phrase_tag_mapping", "phrase", "varchar", false);
        assertColumn("jd_phrase_tag_mapping", "normalized_phrase", "varchar", false);
        assertColumn("jd_phrase_tag_mapping", "tag_code", "varchar", false);
        assertColumn("jd_phrase_tag_mapping", "confidence", "decimal", false);
        assertColumn("jd_phrase_tag_mapping", "enabled", "tinyint", false);
        assertColumn("jd_phrase_tag_mapping", "created_at", "datetime", false);
    }

    @Test
    void relationOutboxAndNormalizationTablesKeepSharedCollectorContract() {
        assertColumn("job_skills", "id", "bigint", false);
        assertColumn("job_skills", "job_id", "bigint", false);
        assertColumn("job_skills", "skill_id", "bigint", false);
        assertColumn("job_skills", "requirement_type", "varchar", false);

        assertColumn("job_experience_tags", "id", "bigint", false);
        assertColumn("job_experience_tags", "job_id", "bigint", false);
        assertColumn("job_experience_tags", "tag_code", "varchar", false);
        assertColumn("job_experience_tags", "source_phrase", "varchar", true);

        assertColumn("outbox_events", "id", "bigint", false);
        assertColumn("outbox_events", "schema_version", "int", false);
        assertColumn("outbox_events", "aggregate_type", "varchar", false);
        assertColumn("outbox_events", "aggregate_id", "bigint", false);
        assertColumn("outbox_events", "event_type", "varchar", false);
        assertColumn("outbox_events", "payload", "json", false);
        assertColumn("outbox_events", "topic", "varchar", false);
        assertColumn("outbox_events", "status", "varchar", false);
        assertColumn("outbox_events", "retry_count", "int", false);
        assertColumn("outbox_events", "last_error", "text", true);
        assertColumn("outbox_events", "created_at", "datetime", false);
        assertColumn("outbox_events", "published_at", "datetime", true);

        assertColumn("normalization_candidates", "id", "bigint", false);
        assertColumn("normalization_candidates", "candidate_type", "varchar", false);
        assertColumn("normalization_candidates", "source", "varchar", false);
        assertColumn("normalization_candidates", "value", "varchar", false);
        assertColumn("normalization_candidates", "normalized_value", "varchar", false);
        assertColumn("normalization_candidates", "occurrence_count", "int", false);
        assertColumn("normalization_candidates", "status", "varchar", false);
        assertColumn("normalization_candidates", "created_at", "datetime", false);
        assertColumn("normalization_candidates", "updated_at", "datetime", false);
    }

    @Test
    void sharedIndexesKeepSearchDedupAndRelationContract() {
        assertIndex("jobs", "uk_jobs_source_external_id", "BTREE");
        assertIndex("jobs", "idx_jobs_canonical_fingerprint", "BTREE");
        assertIndex("jobs", "idx_jobs_source_last_seen", "BTREE");
        assertIndex("jobs", "idx_jobs_source_status", "BTREE");
        assertIndex("jobs", "ft_jobs_search", "FULLTEXT");

        assertIndex("job_skills", "uk_job_skill_requirement", "BTREE");
        assertIndex("job_experience_tags", "uk_job_experience_tag", "BTREE");
        assertIndex("skill_aliases", "uk_skill_aliases_normalized_alias", "BTREE");
        assertIndex("normalization_candidates", "uk_normalization_candidates_type_source_value", "BTREE");
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

    private void assertColumn(String tableName, String columnName, String dataType, boolean nullable) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                tableName,
                columnName
        );

        assertThat(rows)
                .as("column should exist: %s.%s", tableName, columnName)
                .hasSize(1);

        Map<String, Object> row = rows.get(0);

        assertThat(row.get("data_type"))
                .as("column data type: %s.%s", tableName, columnName)
                .isEqualTo(dataType);

        assertThat(row.get("is_nullable"))
                .as("column nullable: %s.%s", tableName, columnName)
                .isEqualTo(nullable ? "YES" : "NO");
    }

    private void assertIndex(String tableName, String indexName, String indexType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                  AND index_type = ?
                """,
                Integer.class,
                tableName,
                indexName,
                indexType
        );

        assertThat(count)
                .as("index should exist: %s.%s", tableName, indexName)
                .isNotNull()
                .isGreaterThan(0);
    }
}
