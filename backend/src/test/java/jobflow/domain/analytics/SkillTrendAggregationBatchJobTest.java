package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class SkillTrendAggregationBatchJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private SkillTrendRepository skillTrendRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSkillRepository jobSkillRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Test
    @DisplayName("Batch job 실행 시 월별 skill trends를 저장한다")
    void runSkillTrendAggregationJob() throws Exception {
        Skill springBoot = skillRepository.save(
                Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
        );
        Skill redis = skillRepository.save(
                Skill.create("Redis", "redis", SkillCategory.DATABASE)
        );
        Job backendJob = jobRepository.save(createJob("batch-backend-spring"));
        Job platformJob = jobRepository.save(createJob("batch-platform-spring-redis"));

        jobSkillRepository.save(JobSkill.create(backendJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(platformJob, springBoot, RequirementType.PREFERRED));
        jobSkillRepository.save(JobSkill.create(platformJob, redis, RequirementType.REQUIRED));
        jobSkillRepository.flush();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("run.id", UUID.randomUUID().toString())
                        .toJobParameters()
        );

        List<SkillTrend> trends = skillTrendRepository
                .findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        LocalDate.of(2026, 6, 1)
                );

        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(trends).hasSize(2);
        assertThat(trends.get(0).getSkill().getId()).isEqualTo(springBoot.getId());
        assertThat(trends.get(0).getJobCount()).isEqualTo(2);
        assertThat(trends.get(0).getRequiredCount()).isEqualTo(1);
        assertThat(trends.get(0).getPreferredCount()).isEqualTo(1);

        assertThat(trends.get(1).getSkill().getId()).isEqualTo(redis.getId());
        assertThat(trends.get(1).getJobCount()).isEqualTo(1);
        assertThat(trends.get(1).getRequiredCount()).isEqualTo(1);
        assertThat(trends.get(1).getPreferredCount()).isEqualTo(0);
    }

    private Job createJob(String externalId) {
        return Job.create(
                "ANALYTICS_BATCH_TEST",
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

    @TestConfiguration
    static class FixedClockTestConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-06-07T01:30:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }
}
