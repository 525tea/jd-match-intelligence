package jobflow.domain.recommendation;

import java.math.BigDecimal;

public final class RecommendationScoreWeights {

    public static final BigDecimal SKILL_MATCH = new BigDecimal("0.40");
    public static final BigDecimal FRESHNESS = new BigDecimal("0.20");
    public static final BigDecimal BEHAVIOR = new BigDecimal("0.30");
    public static final BigDecimal POPULARITY = new BigDecimal("0.10");

    private RecommendationScoreWeights() {
    }
}
