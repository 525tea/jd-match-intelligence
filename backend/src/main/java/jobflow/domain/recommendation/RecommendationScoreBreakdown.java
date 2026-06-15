package jobflow.domain.recommendation;

import java.math.BigDecimal;

public record RecommendationScoreBreakdown(
        BigDecimal totalScore,
        BigDecimal skillMatchScore,
        BigDecimal freshnessScore,
        BigDecimal behaviorScore,
        BigDecimal popularityScore,
        BigDecimal skillMatchRate,
        BigDecimal freshnessRate,
        BigDecimal behaviorRate,
        BigDecimal popularityRate
) {
}
