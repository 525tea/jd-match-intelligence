package jobflow.domain.analytics;

import java.time.LocalDate;

public record JobMarketStatsAggregationResult(
        AnalyticsPeriodType periodType,
        LocalDate periodStart,
        int sourceCount,
        int savedCount
) {
}
