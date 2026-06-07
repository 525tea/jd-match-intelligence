package jobflow.domain.analytics;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
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
        SkillTrendAggregationResult result = skillTrendAggregationService.aggregateMonthly(LocalDate.now(clock));
        log.info(
                "Skill trend aggregation completed. periodType={}, periodStart={}, sourceCount={}, savedCount={}",
                result.periodType(),
                result.periodStart(),
                result.sourceCount(),
                result.savedCount()
        );
    }
}
