package jobflow.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendationScoreCalculatorTest {

    private final RecommendationScoreCalculator calculator = new RecommendationScoreCalculator();

    @Test
    @DisplayName("추천 점수는 skill, freshness, behavior, popularity factor를 가중합으로 계산한다")
    void calculateRecommendationScore() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);

        RecommendationScoreBreakdown breakdown = calculator.calculate(new RecommendationScoreInput(
                0.80,
                now.minusDays(3),
                now.plusDays(7),
                RecommendationBehaviorSignal.NONE,
                0.60,
                now
        ));

        assertThat(breakdown.skillMatchScore()).isEqualByComparingTo("32.00");
        assertThat(breakdown.freshnessScore()).isEqualByComparingTo("18.00");
        assertThat(breakdown.behaviorScore()).isEqualByComparingTo("30.00");
        assertThat(breakdown.popularityScore()).isEqualByComparingTo("6.00");
        assertThat(breakdown.totalScore()).isEqualByComparingTo("86.00");

        assertThat(breakdown.skillMatchRate()).isEqualByComparingTo("80.00");
        assertThat(breakdown.freshnessRate()).isEqualByComparingTo("90.00");
        assertThat(breakdown.behaviorRate()).isEqualByComparingTo("100.00");
        assertThat(breakdown.popularityRate()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("cold start처럼 skill match rate가 없으면 skill factor를 neutral로 계산한다")
    void calculateRecommendationScoreWithColdStartSkillNeutral() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);

        RecommendationScoreBreakdown breakdown = calculator.calculate(new RecommendationScoreInput(
                null,
                now.minusDays(15),
                null,
                RecommendationBehaviorSignal.NONE,
                0.80,
                now
        ));

        assertThat(breakdown.skillMatchScore()).isEqualByComparingTo("20.00");
        assertThat(breakdown.freshnessScore()).isEqualByComparingTo("10.00");
        assertThat(breakdown.behaviorScore()).isEqualByComparingTo("30.00");
        assertThat(breakdown.popularityScore()).isEqualByComparingTo("8.00");
        assertThat(breakdown.totalScore()).isEqualByComparingTo("68.00");
    }

    @Test
    @DisplayName("SAVED와 VIEWED 공고는 행동 기반 점수에서 감쇠된다")
    void calculateRecommendationScoreWithBehaviorDecay() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);

        RecommendationScoreBreakdown none = calculator.calculate(scoreInput(RecommendationBehaviorSignal.NONE, now));
        RecommendationScoreBreakdown viewed = calculator.calculate(scoreInput(RecommendationBehaviorSignal.VIEWED, now));
        RecommendationScoreBreakdown saved = calculator.calculate(scoreInput(RecommendationBehaviorSignal.SAVED, now));

        assertThat(none.behaviorScore()).isEqualByComparingTo("30.00");
        assertThat(viewed.behaviorScore()).isEqualByComparingTo("21.00");
        assertThat(saved.behaviorScore()).isEqualByComparingTo("12.00");

        assertThat(none.totalScore()).isGreaterThan(viewed.totalScore());
        assertThat(viewed.totalScore()).isGreaterThan(saved.totalScore());
    }

    @Test
    @DisplayName("deadlineAt이 null이어도 freshness 점수에 불이익을 주지 않는다")
    void calculateRecommendationScoreWithoutDeadlinePenalty() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);

        RecommendationScoreBreakdown withDeadline = calculator.calculate(new RecommendationScoreInput(
                0.70,
                now.minusDays(5),
                now.plusDays(10),
                RecommendationBehaviorSignal.NONE,
                0.50,
                now
        ));
        RecommendationScoreBreakdown withoutDeadline = calculator.calculate(new RecommendationScoreInput(
                0.70,
                now.minusDays(5),
                null,
                RecommendationBehaviorSignal.NONE,
                0.50,
                now
        ));

        assertThat(withoutDeadline.freshnessScore()).isEqualByComparingTo(withDeadline.freshnessScore());
        assertThat(withoutDeadline.totalScore()).isEqualByComparingTo(withDeadline.totalScore());
    }

    @Test
    @DisplayName("입력 rate는 0과 1 사이로 clamp한다")
    void clampRates() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 12, 0);

        RecommendationScoreBreakdown breakdown = calculator.calculate(new RecommendationScoreInput(
                2.0,
                now.minusDays(40),
                null,
                RecommendationBehaviorSignal.NONE,
                -1.0,
                now
        ));

        assertThat(breakdown.skillMatchScore()).isEqualByComparingTo("40.00");
        assertThat(breakdown.freshnessScore()).isEqualByComparingTo("0.00");
        assertThat(breakdown.popularityScore()).isEqualByComparingTo("0.00");
        assertThat(breakdown.totalScore()).isEqualByComparingTo("70.00");
    }

    private RecommendationScoreInput scoreInput(
            RecommendationBehaviorSignal behaviorSignal,
            LocalDateTime now
    ) {
        return new RecommendationScoreInput(
                0.80,
                now.minusDays(3),
                null,
                behaviorSignal,
                0.60,
                now
        );
    }
}
