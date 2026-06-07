package jobflow.domain.analytics;

import java.time.LocalDate;

public record SkillTrendAggregationResult(
        AnalyticsPeriodType periodType,
        LocalDate periodStart,
        int sourceCount,
        int savedCount
) {
}
