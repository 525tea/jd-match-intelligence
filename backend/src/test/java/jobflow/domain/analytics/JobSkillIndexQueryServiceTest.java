package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobSkill;
import jobflow.domain.job.JobSkillRepository;
import jobflow.domain.job.RemoteType;
import jobflow.domain.job.RequirementType;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        JpaAuditingConfig.class,
        JobSkillIndexRebuildService.class,
        JobSkillIndexQueryService.class
})
class JobSkillIndexQueryServiceTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private JobSkillRepository jobSkillRepository;

    @Autowired
    private JobSkillIndexRebuildService jobSkillIndexRebuildService;

    @Autowired
    private JobSkillIndexQueryService jobSkillIndexQueryService;

    @Test
    @DisplayName("보유 스킬 기준으로 open job의 required/preferred 매칭 요약을 조회한다")
    void findTopOpenJobMatches() {
        String suffix = UUID.randomUUID().toString();

        Skill java = saveSkill("Query Java", "query-java", SkillCategory.LANGUAGE, suffix);
        Skill spring = saveSkill("Query Spring", "query-spring", SkillCategory.FRAMEWORK, suffix);
        Skill redis = saveSkill("Query Redis", "query-redis", SkillCategory.INFRA, suffix);
        Skill kotlin = saveSkill("Query Kotlin", "query-kotlin", SkillCategory.LANGUAGE, suffix);

        Job strongMatchJob = jobRepository.save(createJob(
                "query-strong-match-" + suffix,
                "Java Spring 백엔드 개발자"
        ));
        jobSkillRepository.save(JobSkill.create(strongMatchJob, java, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(strongMatchJob, spring, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(strongMatchJob, redis, RequirementType.PREFERRED));

        Job weakMatchJob = jobRepository.save(createJob(
                "query-weak-match-" + suffix,
                "Kotlin 백엔드 개발자"
        ));
        jobSkillRepository.save(JobSkill.create(weakMatchJob, kotlin, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(weakMatchJob, redis, RequirementType.PREFERRED));

        jobRepository.flush();
        jobSkillRepository.flush();

        jobSkillIndexRebuildService.rebuild();

        List<JobSkillMatchSummary> summaries = jobSkillIndexQueryService.findTopOpenJobMatches(
                List.of(java.getId(), spring.getId()),
                10
        );

        JobSkillMatchSummary strongMatchSummary = summaries.stream()
                .filter(summary -> summary.jobId().equals(strongMatchJob.getId()))
                .findFirst()
                .orElseThrow();
        JobSkillMatchSummary weakMatchSummary = summaries.stream()
                .filter(summary -> summary.jobId().equals(weakMatchJob.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(summaries.indexOf(strongMatchSummary)).isLessThan(summaries.indexOf(weakMatchSummary));

        assertThat(strongMatchSummary.requiredSkillCount()).isEqualTo(2);
        assertThat(strongMatchSummary.matchedRequiredSkillCount()).isEqualTo(2);
        assertThat(strongMatchSummary.preferredSkillCount()).isEqualTo(1);
        assertThat(strongMatchSummary.matchedPreferredSkillCount()).isZero();
        assertThat(strongMatchSummary.missingRequiredSkillCount()).isZero();
        assertThat(strongMatchSummary.requiredMatchRate()).isEqualTo(1.0);

        assertThat(weakMatchSummary.requiredSkillCount()).isEqualTo(1);
        assertThat(weakMatchSummary.matchedRequiredSkillCount()).isZero();
        assertThat(weakMatchSummary.preferredSkillCount()).isEqualTo(1);
        assertThat(weakMatchSummary.matchedPreferredSkillCount()).isZero();
        assertThat(weakMatchSummary.missingRequiredSkillCount()).isEqualTo(1);
        assertThat(weakMatchSummary.requiredMatchRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("보유 스킬이 비어 있어도 전체 open job 매칭 요약을 안전하게 조회한다")
    void findTopOpenJobMatchesWithEmptySkillIds() {
        String suffix = UUID.randomUUID().toString();

        Skill java = saveSkill("Empty Java", "empty-java", SkillCategory.LANGUAGE, suffix);
        Job job = jobRepository.save(createJob(
                "query-empty-skill-" + suffix,
                "Java 백엔드 개발자"
        ));
        jobSkillRepository.save(JobSkill.create(job, java, RequirementType.REQUIRED));

        jobRepository.flush();
        jobSkillRepository.flush();

        jobSkillIndexRebuildService.rebuild();

        List<JobSkillMatchSummary> summaries = jobSkillIndexQueryService.findTopOpenJobMatches(
                List.of(),
                10
        );

        JobSkillMatchSummary summary = summaries.stream()
                .filter(candidate -> candidate.jobId().equals(job.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(summary.requiredSkillCount()).isEqualTo(1);
        assertThat(summary.matchedRequiredSkillCount()).isZero();
        assertThat(summary.missingRequiredSkillCount()).isEqualTo(1);
    }

    private Skill saveSkill(
            String namePrefix,
            String normalizedPrefix,
            SkillCategory category,
            String suffix
    ) {
        return skillRepository.save(Skill.create(
                namePrefix + " " + suffix,
                normalizedPrefix + "-" + suffix,
                category
        ));
    }

    private Job createJob(String externalId, String title) {
        return Job.create(
                "GAP_INDEX_QUERY_TEST",
                externalId,
                title,
                "JobFlow",
                "자격요건 Java Spring 우대사항 Redis",
                "https://example.com/jobs/" + externalId,
                JobRole.BACKEND,
                "Backend",
                CareerLevel.JUNIOR,
                1,
                3,
                null,
                EmploymentType.FULL_TIME,
                null,
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.ONSITE,
                null,
                null,
                "KRW",
                false,
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
    }
}
