package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import jobflow.global.config.JpaAuditingConfig;
import org.springframework.context.annotation.Import;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JobSkillTrendAggregationRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSkillRepository jobSkillRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Test
    @DisplayName("기간 내 공고 스킬을 월별 트렌드 원천 데이터로 집계한다")
    void aggregateSkillTrends() {
        Skill springBoot = skillRepository.save(
                Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
        );
        Skill redis = skillRepository.save(
                Skill.create("Redis", "redis", SkillCategory.DATABASE)
        );
        Job backendJob = jobRepository.save(createJob("backend-spring"));
        Job platformJob = jobRepository.save(createJob("platform-spring-redis"));

        jobSkillRepository.save(JobSkill.create(backendJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(platformJob, springBoot, RequirementType.PREFERRED));
        jobSkillRepository.save(JobSkill.create(platformJob, redis, RequirementType.REQUIRED));
        jobSkillRepository.flush();

        List<JobSkillTrendAggregate> aggregates = jobSkillRepository.aggregateSkillTrends(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 0, 0)
        );

        assertThat(aggregates).hasSize(2);
        assertThat(aggregates.get(0).skill().getName()).isEqualTo("Spring Boot");
        assertThat(aggregates.get(0).jobCount()).isEqualTo(2);
        assertThat(aggregates.get(0).requiredCount()).isEqualTo(1);
        assertThat(aggregates.get(0).preferredCount()).isEqualTo(1);

        assertThat(aggregates.get(1).skill().getName()).isEqualTo("Redis");
        assertThat(aggregates.get(1).jobCount()).isEqualTo(1);
        assertThat(aggregates.get(1).requiredCount()).isEqualTo(1);
        assertThat(aggregates.get(1).preferredCount()).isEqualTo(0);
    }

    private Job createJob(String externalId) {
        return Job.create(
                "ANALYTICS_TEST",
                externalId,
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 API 개발",
                "https://example.com/jobs/" + externalId,
                JobRole.BACKEND,
                "Java Spring Boot JPA",
                CareerLevel.JUNIOR,
                0,
                3,
                null,
                EmploymentType.FULL_TIME,
                null,
                "IT",
                "KR",
                "Seoul",
                "Gangnam",
                RemoteType.HYBRID,
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
