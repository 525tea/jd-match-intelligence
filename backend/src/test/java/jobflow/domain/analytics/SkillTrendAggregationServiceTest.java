package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, SkillTrendAggregationService.class})
class SkillTrendAggregationServiceTest {

    @Autowired
    private SkillTrendAggregationService skillTrendAggregationService;

    @Autowired
    private SkillTrendRepository skillTrendRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSkillRepository jobSkillRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Test
    @DisplayName("월별 공고 스킬 집계를 skill trends에 저장한다")
    void aggregateMonthlySkillTrends() {
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

        SkillTrendAggregationResult result = skillTrendAggregationService.aggregateMonthly(LocalDate.of(2026, 6, 15));

        List<SkillTrend> trends = skillTrendRepository
                .findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        LocalDate.of(2026, 6, 1)
                );

        assertThat(result.periodType()).isEqualTo(AnalyticsPeriodType.MONTHLY);
        assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.sourceCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(trends).hasSize(2);
        assertThat(trends.get(0).getSkill().getName()).isEqualTo("Spring Boot");
        assertThat(trends.get(0).getJobCount()).isEqualTo(2);
        assertThat(trends.get(0).getRequiredCount()).isEqualTo(1);
        assertThat(trends.get(0).getPreferredCount()).isEqualTo(1);
        assertThat(trends.get(0).getTrendScore()).isEqualByComparingTo("3");

        assertThat(trends.get(1).getSkill().getName()).isEqualTo("Redis");
        assertThat(trends.get(1).getJobCount()).isEqualTo(1);
        assertThat(trends.get(1).getRequiredCount()).isEqualTo(1);
        assertThat(trends.get(1).getPreferredCount()).isEqualTo(0);
        assertThat(trends.get(1).getTrendScore()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("같은 월 집계를 다시 실행하면 기존 skill trends를 교체한다")
    void replaceMonthlySkillTrends() {
        Skill springBoot = skillRepository.save(
                Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
        );
        Job backendJob = jobRepository.save(createJob("backend-spring"));

        skillTrendRepository.save(SkillTrend.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                springBoot,
                99,
                99,
                0,
                java.math.BigDecimal.valueOf(198)
        ));
        jobSkillRepository.save(JobSkill.create(backendJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.flush();

        SkillTrendAggregationResult result = skillTrendAggregationService.aggregateMonthly(LocalDate.of(2026, 6, 1));

        List<SkillTrend> trends = skillTrendRepository
                .findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        LocalDate.of(2026, 6, 1)
                );

        assertThat(result.sourceCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).getJobCount()).isEqualTo(1);
        assertThat(trends.get(0).getRequiredCount()).isEqualTo(1);
        assertThat(trends.get(0).getTrendScore()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("집계 대상이 없으면 해당 월 기존 skill trends도 비운다")
    void clearMonthlySkillTrendsWhenNoSourceDataExists() {
        Skill springBoot = skillRepository.save(
                Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
        );
        skillTrendRepository.save(SkillTrend.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                springBoot,
                10,
                8,
                2,
                java.math.BigDecimal.valueOf(18)
        ));
        skillTrendRepository.flush();

        SkillTrendAggregationResult result = skillTrendAggregationService.aggregateMonthly(LocalDate.of(2026, 6, 1));

        List<SkillTrend> trends = skillTrendRepository
                .findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        LocalDate.of(2026, 6, 1)
                );

        assertThat(result.sourceCount()).isZero();
        assertThat(result.savedCount()).isZero();
        assertThat(trends).isEmpty();
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
