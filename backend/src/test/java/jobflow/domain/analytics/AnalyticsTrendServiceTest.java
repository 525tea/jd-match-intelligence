package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jobflow.domain.analytics.dto.JobMarketStatsResponse;
import jobflow.domain.analytics.dto.SkillCooccurrenceResponse;
import jobflow.domain.analytics.dto.SkillExperienceMarketResponse;
import jobflow.domain.analytics.dto.SkillTrendResponse;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsTrendServiceTest {

    @Mock
    private SkillTrendRepository skillTrendRepository;

    @Mock
    private SkillCooccurrenceRepository skillCooccurrenceRepository;

    @Mock
    private SkillExperienceMarketRepository skillExperienceMarketRepository;

    @Mock
    private JobMarketStatsRepository jobMarketStatsRepository;

    @InjectMocks
    private AnalyticsTrendService analyticsTrendService;

    @Test
    @DisplayName("월별 스킬 트렌드를 조회한다")
    void getSkillTrends() {
        LocalDate month = LocalDate.of(2026, 6, 15);
        LocalDate periodStart = LocalDate.of(2026, 6, 1);
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill redis = Skill.create("Redis", "redis", SkillCategory.DATABASE);
        SkillTrend springTrend = SkillTrend.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                springBoot,
                10,
                7,
                3,
                BigDecimal.valueOf(17)
        );
        SkillTrend redisTrend = SkillTrend.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                redis,
                5,
                2,
                3,
                BigDecimal.valueOf(7)
        );

        given(skillTrendRepository.findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                AnalyticsPeriodType.MONTHLY,
                periodStart
        )).willReturn(List.of(springTrend, redisTrend));

        List<SkillTrendResponse> responses = analyticsTrendService.getSkillTrends(month, 1);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).skillName()).isEqualTo("Spring Boot");
        assertThat(responses.get(0).jobCount()).isEqualTo(10);
        assertThat(responses.get(0).trendScore()).isEqualByComparingTo("17");
    }

    @Test
    @DisplayName("특정 스킬의 동시 등장 스킬을 조회한다")
    void getSkillCooccurrences() {
        LocalDate periodStart = LocalDate.of(2026, 6, 1);
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        Skill redis = Skill.create("Redis", "redis", SkillCategory.DATABASE);
        SkillCooccurrence cooccurrence = SkillCooccurrence.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                springBoot,
                redis,
                4,
                10,
                6,
                BigDecimal.valueOf(1.8)
        );

        given(skillCooccurrenceRepository.findByPeriodTypeAndPeriodStartAndBaseSkillIdOrderByLiftScoreDesc(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                1L
        )).willReturn(List.of(cooccurrence));

        List<SkillCooccurrenceResponse> responses =
                analyticsTrendService.getSkillCooccurrences(periodStart, 1L, 20);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).baseSkillName()).isEqualTo("Spring Boot");
        assertThat(responses.get(0).coSkillName()).isEqualTo("Redis");
        assertThat(responses.get(0).cooccurrenceCount()).isEqualTo(4);
        assertThat(responses.get(0).liftScore()).isEqualByComparingTo("1.8");
    }

    @Test
    @DisplayName("특정 스킬의 경험 태그 시장 데이터를 조회한다")
    void getSkillExperienceMarkets() {
        LocalDate periodStart = LocalDate.of(2026, 6, 1);
        Skill springBoot = Skill.create("Spring Boot", "spring boot", SkillCategory.FRAMEWORK);
        ExperienceTagCode traffic = ExperienceTagCodeTestFactory.create(
                "HIGH_TRAFFIC_TEST",
                "대용량 트래픽",
                "대용량 트래픽 처리 경험"
        );
        SkillExperienceMarket market = SkillExperienceMarket.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                springBoot,
                traffic,
                3,
                10,
                5,
                BigDecimal.valueOf(2.1)
        );

        given(skillExperienceMarketRepository.findByPeriodTypeAndPeriodStartAndSkillIdOrderByLiftScoreDesc(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                1L
        )).willReturn(List.of(market));

        List<SkillExperienceMarketResponse> responses =
                analyticsTrendService.getSkillExperienceMarkets(periodStart, 1L, 20);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).skillName()).isEqualTo("Spring Boot");
        assertThat(responses.get(0).tagCode()).isEqualTo("HIGH_TRAFFIC_TEST");
        assertThat(responses.get(0).tagName()).isEqualTo("대용량 트래픽");
        assertThat(responses.get(0).jobCount()).isEqualTo(3);
        assertThat(responses.get(0).liftScore()).isEqualByComparingTo("2.1");
    }

    @Test
    @DisplayName("공고 시장 통계를 조회한다")
    void getJobMarketStats() {
        LocalDate periodStart = LocalDate.of(2026, 6, 1);
        JobMarketStats stats = JobMarketStats.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                "HYBRID",
                12,
                10,
                1,
                1,
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(4.0)
        );

        given(jobMarketStatsRepository.findByPeriodTypeAndPeriodStartAndRoleOrderByJobCountDesc(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                JobRole.BACKEND
        )).willReturn(List.of(stats));

        List<JobMarketStatsResponse> responses =
                analyticsTrendService.getJobMarketStats(periodStart, JobRole.BACKEND, 20);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).role()).isEqualTo(JobRole.BACKEND);
        assertThat(responses.get(0).careerLevel()).isEqualTo(CareerLevel.JUNIOR);
        assertThat(responses.get(0).locationRegion()).isEqualTo("Seoul");
        assertThat(responses.get(0).remoteType()).isEqualTo("HYBRID");
        assertThat(responses.get(0).jobCount()).isEqualTo(12);
        assertThat(responses.get(0).avgMinExperienceYears()).isEqualByComparingTo("1.5");
    }
}
