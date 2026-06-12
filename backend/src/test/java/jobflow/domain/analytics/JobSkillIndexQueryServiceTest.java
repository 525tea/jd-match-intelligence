package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
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
                "Java Spring 백엔드 개발자",
                JobRole.BACKEND
        ));
        jobSkillRepository.save(JobSkill.create(strongMatchJob, java, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(strongMatchJob, spring, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(strongMatchJob, redis, RequirementType.PREFERRED));

        Job weakMatchJob = jobRepository.save(createJob(
                "query-weak-match-" + suffix,
                "Kotlin 백엔드 개발자",
                JobRole.BACKEND
        ));
        jobSkillRepository.save(JobSkill.create(weakMatchJob, kotlin, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(weakMatchJob, redis, RequirementType.PREFERRED));

        Job outOfTargetRoleJob = jobRepository.save(createJob(
                "query-frontend-match-" + suffix,
                "Java Spring 프론트엔드 개발자",
                JobRole.FRONTEND
        ));
        jobSkillRepository.save(JobSkill.create(outOfTargetRoleJob, java, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(outOfTargetRoleJob, spring, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(outOfTargetRoleJob, redis, RequirementType.PREFERRED));

        jobRepository.flush();
        jobSkillRepository.flush();

        jobSkillIndexRebuildService.rebuild();

        List<JobSkillMatchSummary> summaries = jobSkillIndexQueryService.findTopOpenJobMatches(
                List.of(java.getId(), spring.getId()),
                List.of(JobRole.BACKEND),
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
        assertThat(summaries)
                .extracting(JobSkillMatchSummary::jobId)
                .doesNotContain(outOfTargetRoleJob.getId());

        assertThat(strongMatchSummary.requiredSkillCount()).isEqualTo(2);
        assertThat(strongMatchSummary.matchedRequiredSkillCount()).isEqualTo(2);
        assertThat(strongMatchSummary.preferredSkillCount()).isEqualTo(1);
        assertThat(strongMatchSummary.matchedPreferredSkillCount()).isZero();
        assertThat(strongMatchSummary.missingRequiredSkillCount()).isZero();
        assertThat(strongMatchSummary.requiredMatchRate()).isEqualTo(1.0);
        assertThat(strongMatchSummary.matchScore()).isEqualTo(70.0);

        assertThat(weakMatchSummary.requiredSkillCount()).isEqualTo(1);
        assertThat(weakMatchSummary.matchedRequiredSkillCount()).isZero();
        assertThat(weakMatchSummary.preferredSkillCount()).isEqualTo(1);
        assertThat(weakMatchSummary.matchedPreferredSkillCount()).isZero();
        assertThat(weakMatchSummary.missingRequiredSkillCount()).isEqualTo(1);
        assertThat(weakMatchSummary.requiredMatchRate()).isEqualTo(0.0);
        assertThat(weakMatchSummary.matchScore()).isEqualTo(-3.0);
    }

    @Test
    @DisplayName("보유 스킬이 비어 있어도 전체 open job 매칭 요약을 안전하게 조회한다")
    void findTopOpenJobMatchesWithEmptySkillIds() {
        String suffix = UUID.randomUUID().toString();

        Skill java = saveSkill("Empty Java", "empty-java", SkillCategory.LANGUAGE, suffix);
        Job job = jobRepository.save(createJob(
                "query-empty-skill-" + suffix,
                "Java 백엔드 개발자",
                JobRole.BACKEND
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
        assertThat(summary.requiredMatchRate()).isEqualTo(0.0);
        assertThat(summary.matchScore()).isEqualTo(-3.0);
    }

    @Test
    @DisplayName("갭 분석 API에서 재사용할 수 있는 매칭 응답 DTO를 생성한다")
    void findTopOpenJobMatchResponses() {
        String suffix = UUID.randomUUID().toString();

        Skill java = saveSkill("Response Java", "response-java", SkillCategory.LANGUAGE, suffix);
        Skill spring = saveSkill("Response Spring", "response-spring", SkillCategory.FRAMEWORK, suffix);
        Skill redis = saveSkill("Response Redis", "response-redis", SkillCategory.INFRA, suffix);

        Job job = jobRepository.save(createJob(
                "query-response-match-" + suffix,
                "Java Spring 백엔드 개발자",
                JobRole.BACKEND
        ));
        jobSkillRepository.save(JobSkill.create(job, java, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(job, spring, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(job, redis, RequirementType.PREFERRED));

        jobRepository.flush();
        jobSkillRepository.flush();

        jobSkillIndexRebuildService.rebuild();

        List<JobSkillMatchResponse> responses = jobSkillIndexQueryService.findTopOpenJobMatchResponses(
                List.of(java.getId(), spring.getId()),
                List.of(JobRole.BACKEND),
                10
        );

        JobSkillMatchResponse response = responses.stream()
                .filter(candidate -> candidate.jobId().equals(job.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(response.role()).isEqualTo(JobRole.BACKEND);
        assertThat(response.requiredSkillCount()).isEqualTo(2);
        assertThat(response.matchedRequiredSkillCount()).isEqualTo(2);
        assertThat(response.missingRequiredSkillCount()).isZero();
        assertThat(response.requiredMatchRate()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(response.preferredSkillCount()).isEqualTo(1);
        assertThat(response.matchedPreferredSkillCount()).isZero();
        assertThat(response.missingPreferredSkillCount()).isEqualTo(1);
        assertThat(response.preferredMatchRate()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(response.matchScore()).isEqualByComparingTo(BigDecimal.valueOf(70.00));
        assertThat(response.matchedRequiredSkills()).containsExactly(
                java.getName(),
                spring.getName()
        );
        assertThat(response.missingRequiredSkills()).isEmpty();
        assertThat(response.matchedPreferredSkills()).isEmpty();
        assertThat(response.missingPreferredSkills()).containsExactly(redis.getName());
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

    private Job createJob(String externalId, String title, JobRole role) {
        return Job.create(
                "GAP_INDEX_QUERY_TEST",
                externalId,
                title,
                "JobFlow",
                "자격요건 Java Spring 우대사항 Redis",
                "https://example.com/jobs/" + externalId,
                role,
                role.name(),
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
