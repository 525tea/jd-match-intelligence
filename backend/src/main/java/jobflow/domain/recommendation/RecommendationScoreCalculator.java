package jobflow.domain.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class RecommendationScoreCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final double NEUTRAL_RATE = 0.50;
    private static final long MAX_FRESHNESS_DAYS = 30;

    public RecommendationScoreBreakdown calculate(RecommendationScoreInput input) {
        BigDecimal skillMatchRate = rate(input.skillMatchRate(), NEUTRAL_RATE);
        BigDecimal freshnessRate = rate(calculateFreshnessRate(input.freshnessBaseAt(), input.now()), NEUTRAL_RATE);
        BigDecimal behaviorRate = rate(behaviorRate(input.behaviorSignal()), 1.00);
        BigDecimal popularityRate = rate(input.popularityRate(), NEUTRAL_RATE);

        BigDecimal skillMatchScore = weightedScore(skillMatchRate, RecommendationScoreWeights.SKILL_MATCH);
        BigDecimal freshnessScore = weightedScore(freshnessRate, RecommendationScoreWeights.FRESHNESS);
        BigDecimal behaviorScore = weightedScore(behaviorRate, RecommendationScoreWeights.BEHAVIOR);
        BigDecimal popularityScore = weightedScore(popularityRate, RecommendationScoreWeights.POPULARITY);

        BigDecimal totalScore = skillMatchScore
                .add(freshnessScore)
                .add(behaviorScore)
                .add(popularityScore)
                .setScale(2, RoundingMode.HALF_UP);

        return new RecommendationScoreBreakdown(
                totalScore,
                skillMatchScore,
                freshnessScore,
                behaviorScore,
                popularityScore,
                toPercent(skillMatchRate),
                toPercent(freshnessRate),
                toPercent(behaviorRate),
                toPercent(popularityRate)
        );
    }

    private double calculateFreshnessRate(LocalDateTime freshnessBaseAt, LocalDateTime now) {
        if (freshnessBaseAt == null) {
            return NEUTRAL_RATE;
        }

        if (freshnessBaseAt.isAfter(now)) {
            return 1.0;
        }

        long ageDays = Duration.between(freshnessBaseAt, now).toDays();
        if (ageDays <= 0) {
            return 1.0;
        }
        if (ageDays >= MAX_FRESHNESS_DAYS) {
            return 0.0;
        }

        return 1.0 - (ageDays / (double) MAX_FRESHNESS_DAYS);
    }

    private double behaviorRate(RecommendationBehaviorSignal behaviorSignal) {
        if (behaviorSignal == null) {
            return RecommendationBehaviorSignal.NONE.rate();
        }

        return behaviorSignal.rate();
    }

    private BigDecimal weightedScore(BigDecimal rate, BigDecimal weight) {
        return rate
                .multiply(weight)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(Double value, double defaultValue) {
        double normalized = value == null ? defaultValue : value;
        return BigDecimal.valueOf(clamp(normalized))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal toPercent(BigDecimal rate) {
        return rate.multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
