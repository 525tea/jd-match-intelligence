package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import jobflow.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, JobMarketStatsAggregationService.class})
class JobMarketStatsAggregationServiceTest {

    @Autowired
    private JobMarketStatsAggregationService jobMarketStatsAggregationService;

    @Autowired
    private JobMarketStatsRepository jobMarketStatsRepository;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("월별 공고 시장 통계를 재생성한다")
    void aggregateMonthlyJobMarketStats() {
        String suffix = UUID.randomUUID().toString();
        LocalDate periodStart = currentPeriodStart();
        jobRepository.save(createJob(
                "market-service-backend-junior-1-" + suffix,
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.HYBRID
        ));
        jobRepository.save(createJob(
                "market-service-backend-junior-2-" + suffix,
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                RemoteType.HYBRID
        ));
        jobRepository.save(createJob(
                "market-service-backend-mid-" + suffix,
                JobRole.BACKEND,
                CareerLevel.MID,
                "Gyeonggi",
                RemoteType.REMOTE
        ));
        jobRepository.flush();

        JobMarketStatsAggregationResult result =
                jobMarketStatsAggregationService.aggregateMonthly(periodStart);

        List<JobMarketStats> stats = jobMarketStatsRepository
                .findByPeriodTypeAndPeriodStartAndRoleOrderByJobCountDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart,
                        JobRole.BACKEND
                );

        assertThat(result.periodType()).isEqualTo(AnalyticsPeriodType.MONTHLY);
        assertThat(result.periodStart()).isEqualTo(periodStart);
        assertThat(result.sourceCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);

        JobMarketStats juniorStats = findStats(stats, CareerLevel.JUNIOR, "Seoul", "HYBRID");
        assertThat(juniorStats.getJobCount()).isEqualTo(2);
        assertThat(juniorStats.getOpenJobCount()).isEqualTo(2);
        assertThat(juniorStats.getAvgMinExperienceYears()).isEqualByComparingTo("0.00");
        assertThat(juniorStats.getAvgMaxExperienceYears()).isEqualByComparingTo("3.00");

        JobMarketStats midStats = findStats(stats, CareerLevel.MID, "Gyeonggi", "REMOTE");
        assertThat(midStats.getJobCount()).isEqualTo(1);
        assertThat(midStats.getOpenJobCount()).isEqualTo(1);
    }

    private JobMarketStats findStats(
            List<JobMarketStats> stats,
            CareerLevel careerLevel,
            String locationRegion,
            String remoteType
    ) {
        return stats.stream()
                .filter(stat -> stat.getCareerLevel() == careerLevel)
                .filter(stat -> stat.getLocationRegion().equals(locationRegion))
                .filter(stat -> stat.getRemoteType().equals(remoteType))
                .findFirst()
                .orElseThrow();
    }

    private LocalDate currentPeriodStart() {
        return LocalDate.now().withDayOfMonth(1);
    }

    private Job createJob(
            String externalId,
            JobRole role,
            CareerLevel careerLevel,
            String locationRegion,
            RemoteType remoteType
    ) {
        return Job.create(
                "ANALYTICS_MARKET_STATS_SERVICE_TEST",
                externalId,
                "백엔드 개발자",
                "JobFlow",
                "Spring Boot 기반 백엔드 API 개발",
                "https://example.com/jobs/" + externalId,
                role,
                "Java Spring Boot JPA",
                careerLevel,
                0,
                3,
                null,
                EmploymentType.FULL_TIME,
                null,
                "IT",
                "KR",
                locationRegion,
                "Gangnam",
                remoteType,
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
