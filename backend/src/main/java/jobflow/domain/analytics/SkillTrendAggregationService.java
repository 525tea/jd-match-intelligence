package jobflow.domain.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.JobSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SkillTrendAggregationService {

    private final JobSkillRepository jobSkillRepository;
    private final SkillTrendRepository skillTrendRepository;

    @Transactional
    public int aggregateMonthly(LocalDate month) {
        LocalDate periodStart = month.withDayOfMonth(1);
        LocalDateTime from = periodStart.atStartOfDay();
        LocalDateTime to = periodStart.plusMonths(1).atStartOfDay();

        List<JobSkillTrendAggregate> aggregates = jobSkillRepository.aggregateSkillTrends(from, to);

        skillTrendRepository.deleteByPeriodTypeAndPeriodStart(AnalyticsPeriodType.MONTHLY, periodStart);

        List<SkillTrend> skillTrends = aggregates.stream()
                .map(aggregate -> SkillTrend.create(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart,
                        aggregate.skill(),
                        aggregate.jobCount(),
                        aggregate.requiredCount(),
                        aggregate.preferredCount(),
                        calculateTrendScore(aggregate)
                ))
                .toList();

        skillTrendRepository.saveAll(skillTrends);
        return skillTrends.size();
    }

    private BigDecimal calculateTrendScore(JobSkillTrendAggregate aggregate) {
        return BigDecimal.valueOf(aggregate.requiredCount() * 2L + aggregate.preferredCount());
    }
}
