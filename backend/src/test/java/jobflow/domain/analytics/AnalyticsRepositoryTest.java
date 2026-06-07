package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AnalyticsRepositoryTest {

    @Autowired
    private SkillTrendRepository skillTrendRepository;

    @Autowired
    private SkillCooccurrenceRepository skillCooccurrenceRepository;

    @Autowired
    private JobMarketStatsRepository jobMarketStatsRepository;

    @Autowired
    private SkillExperienceMarketRepository skillExperienceMarketRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private ExperienceTagCodeRepository experienceTagCodeRepository;

    @Test
    @DisplayName("스킬 트렌드 집계 데이터를 저장한다")
    void saveSkillTrend() {
        Skill springBoot = skillRepository.save(
                Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
        );
        SkillTrend skillTrend = SkillTrend.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                springBoot,
                15,
                10,
                5,
                BigDecimal.valueOf(12.5000)
        );

        SkillTrend saved = skillTrendRepository.saveAndFlush(skillTrend);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPeriodType()).isEqualTo(AnalyticsPeriodType.MONTHLY);
        assertThat(saved.getPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(saved.getSkill().getName()).isEqualTo("Spring Boot");
        assertThat(saved.getJobCount()).isEqualTo(15);
        assertThat(saved.getRequiredCount()).isEqualTo(10);
        assertThat(saved.getPreferredCount()).isEqualTo(5);
        assertThat(saved.getComputedAt()).isNotNull();
    }

    @Test
    @DisplayName("스킬 동시 출현 집계 데이터를 저장한다")
    void saveSkillCooccurrence() {
        Skill springBoot = skillRepository.save(
                Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
        );
        Skill redis = skillRepository.save(
                Skill.create("Redis", "redis", SkillCategory.DATABASE)
        );
        SkillCooccurrence cooccurrence = SkillCooccurrence.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                springBoot,
                redis,
                8,
                15,
                10,
                BigDecimal.valueOf(1.7500)
        );

        SkillCooccurrence saved = skillCooccurrenceRepository.saveAndFlush(cooccurrence);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBaseSkill().getName()).isEqualTo("Spring Boot");
        assertThat(saved.getCoSkill().getName()).isEqualTo("Redis");
        assertThat(saved.getCooccurrenceCount()).isEqualTo(8);
        assertThat(saved.getBaseSkillJobCount()).isEqualTo(15);
        assertThat(saved.getCoSkillJobCount()).isEqualTo(10);
        assertThat(saved.getComputedAt()).isNotNull();
    }

    @Test
    @DisplayName("공고 시장 통계 집계 데이터를 저장한다")
    void saveJobMarketStats() {
        JobMarketStats stats = JobMarketStats.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                "HYBRID",
                30,
                25,
                3,
                2,
                BigDecimal.valueOf(1.50),
                BigDecimal.valueOf(4.00)
        );

        JobMarketStats saved = jobMarketStatsRepository.saveAndFlush(stats);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(JobRole.BACKEND);
        assertThat(saved.getCareerLevel()).isEqualTo(CareerLevel.JUNIOR);
        assertThat(saved.getLocationRegion()).isEqualTo("Seoul");
        assertThat(saved.getRemoteType()).isEqualTo("HYBRID");
        assertThat(saved.getJobCount()).isEqualTo(30);
        assertThat(saved.getOpenJobCount()).isEqualTo(25);
        assertThat(saved.getClosedJobCount()).isEqualTo(3);
        assertThat(saved.getExpiredJobCount()).isEqualTo(2);
        assertThat(saved.getComputedAt()).isNotNull();
    }

    @Test
    @DisplayName("공고 시장 통계의 빈 차원 값은 ALL로 저장한다")
    void saveJobMarketStatsWithAllDimension() {
        JobMarketStats stats = JobMarketStats.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                JobRole.BACKEND,
                CareerLevel.ANY,
                null,
                " ",
                30,
                25,
                3,
                2,
                null,
                null
        );

        JobMarketStats saved = jobMarketStatsRepository.saveAndFlush(stats);

        assertThat(saved.getLocationRegion()).isEqualTo(JobMarketStats.DIMENSION_ALL);
        assertThat(saved.getRemoteType()).isEqualTo(JobMarketStats.DIMENSION_ALL);
    }

    @Test
    @DisplayName("스킬-경험 태그 시장 집계 데이터를 저장한다")
    void saveSkillExperienceMarket() {
        Skill springBoot = skillRepository.save(
                Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK)
        );
        ExperienceTagCode highTraffic = experienceTagCodeRepository.save(
                createExperienceTagCode("HIGH_TRAFFIC", "대용량 트래픽", "대용량 트래픽 처리 경험")
        );
        SkillExperienceMarket market = SkillExperienceMarket.create(
                AnalyticsPeriodType.MONTHLY,
                LocalDate.of(2026, 6, 1),
                springBoot,
                highTraffic,
                7,
                15,
                9,
                BigDecimal.valueOf(1.4000)
        );

        SkillExperienceMarket saved = skillExperienceMarketRepository.saveAndFlush(market);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSkill().getName()).isEqualTo("Spring Boot");
        assertThat(saved.getTagCode().getCode()).isEqualTo("HIGH_TRAFFIC");
        assertThat(saved.getJobCount()).isEqualTo(7);
        assertThat(saved.getSkillJobCount()).isEqualTo(15);
        assertThat(saved.getTagJobCount()).isEqualTo(9);
        assertThat(saved.getComputedAt()).isNotNull();
    }

    private ExperienceTagCode createExperienceTagCode(
            String code,
            String name,
            String description
    ) {
        return ExperienceTagCodeTestFactory.create(code, name, description);
    }
}
