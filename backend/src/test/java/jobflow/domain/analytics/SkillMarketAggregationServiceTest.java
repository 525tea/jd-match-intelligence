package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobExperienceTag;
import jobflow.domain.job.JobExperienceTagRepository;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobSkill;
import jobflow.domain.job.JobSkillRepository;
import jobflow.domain.job.RemoteType;
import jobflow.domain.job.RequirementType;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, SkillMarketAggregationService.class})
class SkillMarketAggregationServiceTest {

    @Autowired
    private SkillMarketAggregationService skillMarketAggregationService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSkillRepository jobSkillRepository;

    @Autowired
    private JobExperienceTagRepository jobExperienceTagRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private ExperienceTagCodeRepository experienceTagCodeRepository;

    @Autowired
    private SkillCooccurrenceRepository skillCooccurrenceRepository;

    @Autowired
    private SkillExperienceMarketRepository skillExperienceMarketRepository;

    @Test
    @DisplayName("월별 스킬 조합과 경험 태그 시장 집계를 저장한다")
    void aggregateMonthlySkillMarket() {
        String suffix = UUID.randomUUID().toString();
        LocalDate periodStart = LocalDate.now().withDayOfMonth(1);

        Skill springBoot = skillRepository.save(
                Skill.create("Market Spring Boot " + suffix, "market-spring-boot-" + suffix, SkillCategory.FRAMEWORK)
        );
        Skill jpa = skillRepository.save(
                Skill.create("Market JPA " + suffix, "market-jpa-" + suffix, SkillCategory.FRAMEWORK)
        );
        Skill redis = skillRepository.save(
                Skill.create("Market Redis " + suffix, "market-redis-" + suffix, SkillCategory.DATABASE)
        );
        ExperienceTagCode traffic = experienceTagCodeRepository.save(
                ExperienceTagCodeTestFactory.create(
                        "MARKET_TRAFFIC_" + suffix.substring(0, 8),
                        "대용량 트래픽",
                        "대용량 트래픽 처리 경험"
                )
        );

        Job backendJob = jobRepository.save(createJob("market-backend-" + suffix));
        Job platformJob = jobRepository.save(createJob("market-platform-" + suffix));

        jobSkillRepository.save(JobSkill.create(backendJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(backendJob, jpa, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(platformJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(platformJob, redis, RequirementType.PREFERRED));

        jobExperienceTagRepository.save(JobExperienceTag.create(backendJob, traffic, "대용량 트래픽"));
        jobExperienceTagRepository.save(JobExperienceTag.create(platformJob, traffic, "대용량 트래픽"));
        jobSkillRepository.flush();
        jobExperienceTagRepository.flush();

        SkillMarketAggregationResult result = skillMarketAggregationService.aggregateMonthly(periodStart);

        List<SkillCooccurrence> springCooccurrences =
                skillCooccurrenceRepository.findByPeriodTypeAndPeriodStartAndBaseSkillIdOrderByLiftScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart,
                        springBoot.getId()
                );
        List<SkillExperienceMarket> springExperienceMarkets =
                skillExperienceMarketRepository.findByPeriodTypeAndPeriodStartAndSkillIdOrderByLiftScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart,
                        springBoot.getId()
                );

        assertThat(result.periodType()).isEqualTo(AnalyticsPeriodType.MONTHLY);
        assertThat(result.periodStart()).isEqualTo(periodStart);
        assertThat(result.cooccurrenceSavedCount()).isEqualTo(4);
        assertThat(result.skillExperienceSavedCount()).isEqualTo(3);

        assertThat(springCooccurrences)
                .extracting(cooccurrence -> cooccurrence.getCoSkill().getId())
                .containsExactlyInAnyOrder(jpa.getId(), redis.getId());

        SkillExperienceMarket springTraffic = springExperienceMarkets.stream()
                .filter(market -> market.getTagCode().getCode().equals(traffic.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(springTraffic.getJobCount()).isEqualTo(2);
        assertThat(springTraffic.getSkillJobCount()).isEqualTo(2);
        assertThat(springTraffic.getTagJobCount()).isEqualTo(2);
        assertThat(springTraffic.getLiftScore()).isEqualByComparingTo(BigDecimal.valueOf(1.0000));
    }

    private Job createJob(String externalId) {
        return Job.create(
                "ANALYTICS_MARKET_SERVICE_TEST",
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
