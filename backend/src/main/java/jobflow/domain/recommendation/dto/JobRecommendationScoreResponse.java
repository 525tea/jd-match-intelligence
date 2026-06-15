package jobflow.domain.recommendation.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import jobflow.domain.recommendation.RecommendationScoreBreakdown;

public record JobRecommendationScoreResponse(
        BigDecimal totalScore,
        BigDecimal skillMatchScore,
        BigDecimal freshnessScore,
        BigDecimal behaviorScore,
        BigDecimal popularityScore,
        BigDecimal skillMatchRate,
        BigDecimal freshnessRate,
        BigDecimal behaviorRate,
        BigDecimal popularityRate
) implements Serializable {

    public static JobRecommendationScoreResponse from(RecommendationScoreBreakdown breakdown) {
        return new JobRecommendationScoreResponse(
                breakdown.totalScore(),
                breakdown.skillMatchScore(),
                breakdown.freshnessScore(),
                breakdown.behaviorScore(),
                breakdown.popularityScore(),
                breakdown.skillMatchRate(),
                breakdown.freshnessRate(),
                breakdown.behaviorRate(),
                breakdown.popularityRate()
        );
    }
}
