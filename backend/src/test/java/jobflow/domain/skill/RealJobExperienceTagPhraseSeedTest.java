package jobflow.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RealJobExperienceTagPhraseSeedTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("ALTER TABLE experience_tag_codes ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE jd_phrase_tag_mapping ALTER COLUMN enabled SET DEFAULT TRUE");
        jdbcTemplate.execute("ALTER TABLE jd_phrase_tag_mapping ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE skills ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V2__seed_skill_and_experience_tags.sql"),
                new ClassPathResource("db/migration/V11__add_real_job_experience_tag_phrases.sql")
        );
        populator.execute(dataSource);
    }

    @Test
    @DisplayName("실제 공고 performance/testing phrase seed가 생성된다")
    void seedPerformanceAndTestingPhrases() {
        assertPhraseTagMapping("최적화", "PERFORMANCE");
        assertPhraseTagMapping("고도화", "PERFORMANCE");
        assertPhraseTagMapping("시뮬레이션", "TESTING");
        assertPhraseTagMapping("검증", "TESTING");
    }

    @Test
    @DisplayName("실제 공고 reliability/cloud/security phrase seed가 생성된다")
    void seedReliabilityCloudSecurityPhrases() {
        assertPhraseTagMapping("유지보수", "RELIABILITY");
        assertPhraseTagMapping("기술 지원", "RELIABILITY");
        assertPhraseTagMapping("시스템 통합", "CLOUD_INFRA");
        assertPhraseTagMapping("환경 구축", "CLOUD_INFRA");
        assertPhraseTagMapping("보안 요구사항", "SECURITY");
        assertPhraseTagMapping("인증 대응", "SECURITY");
    }

    private void assertPhraseTagMapping(String normalizedPhrase, String tagCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM jd_phrase_tag_mapping
                        WHERE normalized_phrase = ?
                          AND tag_code = ?
                          AND enabled = TRUE
                        """,
                Integer.class,
                normalizedPhrase,
                tagCode
        );

        assertThat(count)
                .as("normalizedPhrase=%s, tagCode=%s", normalizedPhrase, tagCode)
                .isPositive();
    }
}
