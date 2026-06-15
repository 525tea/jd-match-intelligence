package jobflow.domain.matching;

import java.math.BigDecimal;
import jobflow.domain.job.CareerLevel;

public record JdMatchScoreInput(
        long requiredSkillCount,
        long matchedRequiredSkillCount,
        long preferredSkillCount,
        long matchedPreferredSkillCount,
        long experienceTagCount,
        long matchedExperienceTagCount,
        CareerLevel targetCareerLevel,
        CareerLevel jobCareerLevel,
        BigDecimal analysisConfidence
) {

    public JdMatchScoreInput {
        requiredSkillCount = nonNegative(requiredSkillCount);
        matchedRequiredSkillCount = bounded(nonNegative(matchedRequiredSkillCount), requiredSkillCount);
        preferredSkillCount = nonNegative(preferredSkillCount);
        matchedPreferredSkillCount = bounded(nonNegative(matchedPreferredSkillCount), preferredSkillCount);
        experienceTagCount = nonNegative(experienceTagCount);
        matchedExperienceTagCount = bounded(nonNegative(matchedExperienceTagCount), experienceTagCount);
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private static long bounded(long value, long max) {
        return Math.min(value, max);
    }
}
