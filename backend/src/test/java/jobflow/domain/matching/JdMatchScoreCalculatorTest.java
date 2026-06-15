package jobflow.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import jobflow.domain.job.CareerLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdMatchScoreCalculatorTest {

    private final JdMatchScoreCalculator calculator = new JdMatchScoreCalculator();

    @Test
    @DisplayName("5-factor JD 매칭 점수를 계산한다")
    void calculateJdMatchScore() {
        JdMatchScoreBreakdown breakdown = calculator.calculate(new JdMatchScoreInput(
                4,
                3,
                2,
                1,
                3,
                2,
                CareerLevel.MID,
                CareerLevel.MID,
                new BigDecimal("0.8000")
        ));

        assertThat(breakdown.requiredSkillRate()).isEqualByComparingTo("75.00");
        assertThat(breakdown.preferredSkillRate()).isEqualByComparingTo("50.00");
        assertThat(breakdown.experienceTagRate()).isEqualByComparingTo("66.67");
        assertThat(breakdown.careerLevelFitRate()).isEqualByComparingTo("100.00");
        assertThat(breakdown.confidenceRate()).isEqualByComparingTo("80.00");

        assertThat(breakdown.requiredSkillScore()).isEqualByComparingTo("33.75");
        assertThat(breakdown.preferredSkillScore()).isEqualByComparingTo("10.00");
        assertThat(breakdown.experienceTagScore()).isEqualByComparingTo("13.33");
        assertThat(breakdown.careerLevelScore()).isEqualByComparingTo("10.00");
        assertThat(breakdown.confidenceScore()).isEqualByComparingTo("4.00");
        assertThat(breakdown.totalScore()).isEqualByComparingTo("71.08");
    }

    @Test
    @DisplayName("required skill 누락은 전체 점수에 크게 반영된다")
    void calculateWithMissingRequiredSkills() {
        JdMatchScoreBreakdown strongMatch = calculator.calculate(new JdMatchScoreInput(
                4,
                4,
                2,
                1,
                2,
                1,
                CareerLevel.JUNIOR,
                CareerLevel.JUNIOR,
                new BigDecimal("0.9000")
        ));
        JdMatchScoreBreakdown weakRequiredMatch = calculator.calculate(new JdMatchScoreInput(
                4,
                1,
                2,
                2,
                2,
                2,
                CareerLevel.JUNIOR,
                CareerLevel.JUNIOR,
                new BigDecimal("0.9000")
        ));

        assertThat(strongMatch.requiredSkillScore()).isEqualByComparingTo("45.00");
        assertThat(weakRequiredMatch.requiredSkillScore()).isEqualByComparingTo("11.25");
        assertThat(strongMatch.totalScore()).isGreaterThan(weakRequiredMatch.totalScore());
    }

    @Test
    @DisplayName("비어 있는 factor bucket은 보너스 점수를 주지 않는다")
    void calculateWithEmptyBuckets() {
        JdMatchScoreBreakdown breakdown = calculator.calculate(new JdMatchScoreInput(
                0,
                0,
                0,
                0,
                0,
                0,
                CareerLevel.ANY,
                CareerLevel.ANY,
                null
        ));

        assertThat(breakdown.requiredSkillRate()).isEqualByComparingTo("0.00");
        assertThat(breakdown.preferredSkillRate()).isEqualByComparingTo("0.00");
        assertThat(breakdown.experienceTagRate()).isEqualByComparingTo("0.00");
        assertThat(breakdown.careerLevelFitRate()).isEqualByComparingTo("50.00");
        assertThat(breakdown.confidenceRate()).isEqualByComparingTo("50.00");

        assertThat(breakdown.requiredSkillScore()).isEqualByComparingTo("0.00");
        assertThat(breakdown.preferredSkillScore()).isEqualByComparingTo("0.00");
        assertThat(breakdown.experienceTagScore()).isEqualByComparingTo("0.00");
        assertThat(breakdown.careerLevelScore()).isEqualByComparingTo("5.00");
        assertThat(breakdown.confidenceScore()).isEqualByComparingTo("2.50");
        assertThat(breakdown.totalScore()).isEqualByComparingTo("7.50");
    }

    @Test
    @DisplayName("career level은 exact, adjacent, distant 순서로 낮아진다")
    void calculateCareerLevelFit() {
        JdMatchScoreBreakdown exact = calculator.calculate(scoreInput(CareerLevel.MID, CareerLevel.MID));
        JdMatchScoreBreakdown adjacent = calculator.calculate(scoreInput(CareerLevel.MID, CareerLevel.JUNIOR));
        JdMatchScoreBreakdown near = calculator.calculate(scoreInput(CareerLevel.MID, CareerLevel.NEWCOMER));
        JdMatchScoreBreakdown far = calculator.calculate(scoreInput(CareerLevel.NEWCOMER, CareerLevel.LEAD));

        assertThat(exact.careerLevelFitRate()).isEqualByComparingTo("100.00");
        assertThat(adjacent.careerLevelFitRate()).isEqualByComparingTo("75.00");
        assertThat(near.careerLevelFitRate()).isEqualByComparingTo("40.00");
        assertThat(far.careerLevelFitRate()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("matched count와 confidence는 유효 범위로 보정된다")
    void calculateWithOutOfRangeInput() {
        JdMatchScoreBreakdown breakdown = calculator.calculate(new JdMatchScoreInput(
                2,
                5,
                2,
                5,
                2,
                5,
                CareerLevel.SENIOR,
                CareerLevel.SENIOR,
                new BigDecimal("1.5000")
        ));

        assertThat(breakdown.requiredSkillRate()).isEqualByComparingTo("100.00");
        assertThat(breakdown.preferredSkillRate()).isEqualByComparingTo("100.00");
        assertThat(breakdown.experienceTagRate()).isEqualByComparingTo("100.00");
        assertThat(breakdown.confidenceRate()).isEqualByComparingTo("100.00");
        assertThat(breakdown.totalScore()).isEqualByComparingTo("100.00");
    }

    private JdMatchScoreInput scoreInput(CareerLevel targetCareerLevel, CareerLevel jobCareerLevel) {
        return new JdMatchScoreInput(
                1,
                1,
                1,
                1,
                1,
                1,
                targetCareerLevel,
                jobCareerLevel,
                BigDecimal.ONE
        );
    }
}
