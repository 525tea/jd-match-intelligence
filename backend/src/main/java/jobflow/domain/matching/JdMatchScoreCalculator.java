package jobflow.domain.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import jobflow.domain.job.CareerLevel;
import org.springframework.stereotype.Component;

@Component
public class JdMatchScoreCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal NEUTRAL_CAREER_FIT = new BigDecimal("0.50");
    private static final BigDecimal ADJACENT_CAREER_FIT = new BigDecimal("0.75");
    private static final BigDecimal NEAR_CAREER_FIT = new BigDecimal("0.40");
    private static final BigDecimal FAR_CAREER_FIT = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.50");

    public JdMatchScoreBreakdown calculate(JdMatchScoreInput input) {
        BigDecimal requiredRate = rate(input.matchedRequiredSkillCount(), input.requiredSkillCount());
        BigDecimal preferredRate = rate(input.matchedPreferredSkillCount(), input.preferredSkillCount());
        BigDecimal experienceTagRate = rate(input.matchedExperienceTagCount(), input.experienceTagCount());
        BigDecimal careerLevelFitRate = careerLevelFitRate(input.targetCareerLevel(), input.jobCareerLevel());
        BigDecimal confidenceRate = confidenceRate(input.analysisConfidence());

        BigDecimal requiredSkillScore = weightedScore(requiredRate, JdMatchScoreWeights.REQUIRED_SKILL);
        BigDecimal preferredSkillScore = weightedScore(preferredRate, JdMatchScoreWeights.PREFERRED_SKILL);
        BigDecimal experienceTagScore = weightedScore(experienceTagRate, JdMatchScoreWeights.EXPERIENCE_TAG);
        BigDecimal careerLevelScore = weightedScore(careerLevelFitRate, JdMatchScoreWeights.CAREER_LEVEL);
        BigDecimal confidenceScore = weightedScore(confidenceRate, JdMatchScoreWeights.CONFIDENCE);

        BigDecimal totalScore = requiredSkillScore
                .add(preferredSkillScore)
                .add(experienceTagScore)
                .add(careerLevelScore)
                .add(confidenceScore)
                .setScale(2, RoundingMode.HALF_UP);

        return new JdMatchScoreBreakdown(
                totalScore,
                requiredSkillScore,
                preferredSkillScore,
                experienceTagScore,
                careerLevelScore,
                confidenceScore,
                percent(requiredRate),
                percent(preferredRate),
                percent(experienceTagRate),
                percent(careerLevelFitRate),
                percent(confidenceRate)
        );
    }

    private BigDecimal rate(long matchedCount, long totalCount) {
        if (totalCount <= 0L) {
            return ZERO;
        }

        return BigDecimal.valueOf(matchedCount)
                .divide(BigDecimal.valueOf(totalCount), 6, RoundingMode.HALF_UP)
                .min(ONE)
                .max(ZERO);
    }

    private BigDecimal careerLevelFitRate(CareerLevel targetCareerLevel, CareerLevel jobCareerLevel) {
        if (targetCareerLevel == null
                || targetCareerLevel == CareerLevel.ANY
                || jobCareerLevel == null
                || jobCareerLevel == CareerLevel.ANY) {
            return NEUTRAL_CAREER_FIT;
        }

        if (targetCareerLevel == jobCareerLevel) {
            return ONE;
        }

        int distance = Math.abs(careerRank(targetCareerLevel) - careerRank(jobCareerLevel));
        if (distance == 1) {
            return ADJACENT_CAREER_FIT;
        }
        if (distance == 2) {
            return NEAR_CAREER_FIT;
        }

        return FAR_CAREER_FIT;
    }

    private int careerRank(CareerLevel careerLevel) {
        return switch (careerLevel) {
            case NEWCOMER -> 0;
            case JUNIOR -> 1;
            case MID -> 2;
            case SENIOR -> 3;
            case LEAD -> 4;
            case ANY -> 2;
        };
    }

    private BigDecimal confidenceRate(BigDecimal analysisConfidence) {
        if (analysisConfidence == null) {
            return DEFAULT_CONFIDENCE;
        }

        return analysisConfidence
                .max(ZERO)
                .min(ONE);
    }

    private BigDecimal weightedScore(BigDecimal rate, BigDecimal weight) {
        return rate.multiply(weight)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal rate) {
        return rate.multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
