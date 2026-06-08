package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobSkill;
import jobflow.domain.job.JobSkillRepository;
import jobflow.domain.job.JobExperienceTag;
import jobflow.domain.job.JobExperienceTagRepository;
import jobflow.domain.job.RemoteType;
import jobflow.domain.job.RequirementType;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SkillTrendAggregationBatchJobTest {

    private static final AtomicLong REQUESTED_AT_SEQUENCE = new AtomicLong(1000L);

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier(SkillTrendAggregationBatchConfig.SKILL_TREND_AGGREGATION_JOB)
    private org.springframework.batch.core.job.Job skillTrendAggregationJob;

    @Autowired
    private SkillTrendRepository skillTrendRepository;

    @Autowired
    private SkillCooccurrenceRepository skillCooccurrenceRepository;

    @Autowired
    private SkillExperienceMarketRepository skillExperienceMarketRepository;

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

    @Test
    @DisplayName("Batch job 실행 시 월별 skill trends를 저장한다")
    void runSkillTrendAggregationJob() throws Exception {
        String suffix = UUID.randomUUID().toString();
        LocalDate periodStart = currentPeriodStart();
        String targetMonth = periodStart.toString();
        Skill springBoot = skillRepository.save(
                Skill.create("Batch Spring Boot " + suffix, "batch-spring-boot-" + suffix, SkillCategory.FRAMEWORK)
        );
        Skill redis = skillRepository.save(
                Skill.create("Batch Redis " + suffix, "batch-redis-" + suffix, SkillCategory.DATABASE)
        );
        ExperienceTagCode traffic = experienceTagCodeRepository.save(
                ExperienceTagCodeTestFactory.create(
                        "BATCH_TRAFFIC_" + suffix,
                        "Batch Traffic " + suffix,
                        "대용량 트래픽 처리"
                )
        );
        Job backendJob = jobRepository.save(createJob(
                "batch-backend-spring-" + suffix,
                LocalDateTime.of(2026, 6, 1, 9, 0)
        ));
        Job platformJob = jobRepository.save(createJob(
                "batch-platform-spring-redis-" + suffix,
                LocalDateTime.of(2026, 6, 2, 9, 0)
        ));

        jobSkillRepository.save(JobSkill.create(backendJob, springBoot, RequirementType.REQUIRED));
        jobSkillRepository.save(JobSkill.create(platformJob, springBoot, RequirementType.PREFERRED));
        jobSkillRepository.save(JobSkill.create(platformJob, redis, RequirementType.REQUIRED));
        jobExperienceTagRepository.save(JobExperienceTag.create(backendJob, traffic, "대용량 트래픽"));
        jobExperienceTagRepository.save(JobExperienceTag.create(platformJob, traffic, "대용량 트래픽"));
        jobSkillRepository.flush();
        jobExperienceTagRepository.flush();

        JobExecution jobExecution = runBatch(targetMonth, nextRequestedAt());

        List<SkillTrend> trends = skillTrendRepository
                .findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart
                );

        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(trends)
                .extracting(trend -> trend.getSkill().getId())
                .contains(springBoot.getId(), redis.getId());

        SkillTrend springBootTrend = findTrend(trends, springBoot);
        assertThat(springBootTrend.getJobCount()).isEqualTo(2);
        assertThat(springBootTrend.getRequiredCount()).isEqualTo(1);
        assertThat(springBootTrend.getPreferredCount()).isEqualTo(1);

        SkillTrend redisTrend = findTrend(trends, redis);
        assertThat(redisTrend.getJobCount()).isEqualTo(1);
        assertThat(redisTrend.getRequiredCount()).isEqualTo(1);
        assertThat(redisTrend.getPreferredCount()).isEqualTo(0);

        List<SkillCooccurrence> cooccurrences = skillCooccurrenceRepository
                .findByPeriodTypeAndPeriodStartAndBaseSkillIdOrderByLiftScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart,
                        springBoot.getId()
                );
        List<SkillExperienceMarket> skillExperienceMarkets = skillExperienceMarketRepository
                .findByPeriodTypeAndPeriodStartAndSkillIdOrderByLiftScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart,
                        springBoot.getId()
                );

        assertThat(cooccurrences)
                .extracting(cooccurrence -> cooccurrence.getCoSkill().getId())
                .contains(redis.getId());

        SkillExperienceMarket springBootTrafficMarket = skillExperienceMarkets.stream()
                .filter(market -> market.getTagCode().getCode().equals(traffic.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(springBootTrafficMarket.getJobCount()).isEqualTo(2);
        assertThat(springBootTrafficMarket.getSkillJobCount()).isEqualTo(2);
        assertThat(springBootTrafficMarket.getTagJobCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 월 Batch job 재실행 시 기존 월별 집계를 교체한다")
    void rerunSkillTrendAggregationJob() throws Exception {
        String suffix = UUID.randomUUID().toString();
        LocalDate periodStart = currentPeriodStart();
        String targetMonth = periodStart.toString();
        Skill firstSkill = skillRepository.save(
                Skill.create("Batch Kotlin " + suffix, "batch-kotlin-" + suffix, SkillCategory.LANGUAGE)
        );
        Skill staleSkill = skillRepository.save(
                Skill.create("Batch Stale " + suffix, "batch-stale-" + suffix, SkillCategory.TOOL)
        );
        skillTrendRepository.save(SkillTrend.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                staleSkill,
                99,
                99,
                0,
                java.math.BigDecimal.valueOf(198)
        ));

        Job firstJob = jobRepository.save(createJob(
                "batch-rerun-kotlin-" + suffix,
                LocalDateTime.of(2026, 5, 1, 9, 0)
        ));
        jobSkillRepository.save(JobSkill.create(firstJob, firstSkill, RequirementType.REQUIRED));
        jobSkillRepository.flush();

        JobExecution firstExecution = runBatch(targetMonth, nextRequestedAt());


        List<SkillTrend> firstTrends = skillTrendRepository
                .findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart
                );

        assertThat(firstExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(firstTrends)
                .extracting(trend -> trend.getSkill().getId())
                .contains(firstSkill.getId())
                .doesNotContain(staleSkill.getId());

        Skill secondSkill = skillRepository.save(
                Skill.create("Batch Kafka " + suffix, "batch-kafka-" + suffix, SkillCategory.INFRA)
        );
        Job secondJob = jobRepository.save(createJob(
                "batch-rerun-kafka-" + suffix,
                LocalDateTime.of(2026, 5, 2, 9, 0)
        ));
        jobSkillRepository.save(JobSkill.create(secondJob, secondSkill, RequirementType.PREFERRED));
        jobSkillRepository.flush();

        JobExecution secondExecution = runBatch(targetMonth, nextRequestedAt());

        List<SkillTrend> secondTrends = skillTrendRepository
                .findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart
                );

        assertThat(secondExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(secondTrends)
                .extracting(trend -> trend.getSkill().getId())
                .contains(firstSkill.getId(), secondSkill.getId())
                .doesNotContain(staleSkill.getId());
    }

    private LocalDate currentPeriodStart() {
        return LocalDate.now().withDayOfMonth(1);
    }

    private long nextRequestedAt() {
        return REQUESTED_AT_SEQUENCE.incrementAndGet();
    }

    private JobExecution runBatch(String targetMonth, long requestedAt) throws Exception {
        return jobOperator.start(
                skillTrendAggregationJob,
                new JobParametersBuilder()
                        .addString("targetMonth", targetMonth)
                        .addLong("requestedAt", requestedAt)
                        .toJobParameters()
        );
    }

    private SkillTrend findTrend(List<SkillTrend> trends, Skill skill) {
        return trends.stream()
                .filter(trend -> trend.getSkill().getId().equals(skill.getId()))
                .findFirst()
                .orElseThrow();
    }

    private Job createJob(String externalId, LocalDateTime createdAt) {
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
                createdAt,
                LocalDateTime.of(2026, 7, 1, 23, 59)
        );
    }
}
