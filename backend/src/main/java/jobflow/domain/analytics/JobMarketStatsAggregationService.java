package jobflow.domain.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobMarketStatsAggregationService {

    private final JobRepository jobRepository;
    private final JobMarketStatsRepository jobMarketStatsRepository;

    @Transactional
    public JobMarketStatsAggregationResult aggregateMonthly(LocalDate month) {
        LocalDate periodStart = month.withDayOfMonth(1);
        LocalDateTime from = periodStart.atStartOfDay();
        LocalDateTime to = periodStart.plusMonths(1).atStartOfDay();

        List<JobMarketStatsAggregate> aggregates = jobRepository.aggregateJobMarketStats(from, to);

        jobMarketStatsRepository.deleteByPeriodTypeAndPeriodStart(
                AnalyticsPeriodType.MONTHLY,
                periodStart
        );
        jobMarketStatsRepository.flush();

        List<JobMarketStats> stats = aggregates.stream()
                .map(aggregate -> toJobMarketStats(periodStart, aggregate))
                .toList();

        jobMarketStatsRepository.saveAll(stats);

        return new JobMarketStatsAggregationResult(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                aggregates.size(),
                stats.size()
        );
    }

    private JobMarketStats toJobMarketStats(
            LocalDate periodStart,
            JobMarketStatsAggregate aggregate
    ) {
        return JobMarketStats.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                aggregate.role(),
                aggregate.careerLevel(),
                aggregate.locationRegion(),
                aggregate.remoteType() == null ? JobMarketStats.DIMENSION_ALL : aggregate.remoteType().name(),
                aggregate.jobCount(),
                aggregate.openJobCount(),
                aggregate.closedJobCount(),
                aggregate.expiredJobCount(),
                toBigDecimal(aggregate.avgMinExperienceYears()),
                toBigDecimal(aggregate.avgMaxExperienceYears())
        );
    }

    private BigDecimal toBigDecimal(Double value) {
        if (value == null) {
            return null;
        }

        return BigDecimal.valueOf(value);
    }
}
