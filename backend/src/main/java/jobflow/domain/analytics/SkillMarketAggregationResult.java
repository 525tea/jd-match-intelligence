package jobflow.domain.analytics;

import java.time.LocalDate;

public record SkillMarketAggregationResult(
        AnalyticsPeriodType periodType,
        LocalDate periodStart,
        int cooccurrenceSourceCount,
        int cooccurrenceSavedCount,
        int skillExperienceSourceCount,
        int skillExperienceSavedCount
) {
}
