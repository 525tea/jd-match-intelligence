package jobflow.domain.recommendation;

import java.time.LocalDateTime;

public record RecommendationScoreInput(
        Double skillMatchRate,
        LocalDateTime freshnessBaseAt,
        LocalDateTime deadlineAt,
        RecommendationBehaviorSignal behaviorSignal,
        Double popularityRate,
        LocalDateTime now
) {

    public RecommendationScoreInput {
        if (now == null) {
            now = LocalDateTime.now();
        }
    }
}
