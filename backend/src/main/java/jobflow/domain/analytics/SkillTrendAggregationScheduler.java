package jobflow.domain.analytics;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SkillTrendAggregationScheduler {

    private final SkillTrendAggregationService skillTrendAggregationService;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${jobflow.analytics.skill-trend.fixed-delay:3600000}",
            initialDelayString = "${jobflow.analytics.skill-trend.initial-delay:60000}"
    )
    public void aggregateCurrentMonthSkillTrends() {
        skillTrendAggregationService.aggregateMonthly(LocalDate.now(clock));
    }
}
