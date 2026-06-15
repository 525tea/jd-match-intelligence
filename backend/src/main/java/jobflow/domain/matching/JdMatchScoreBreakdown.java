package jobflow.domain.matching;

import java.math.BigDecimal;

public record JdMatchScoreBreakdown(
        BigDecimal totalScore,
        BigDecimal requiredSkillScore,
        BigDecimal preferredSkillScore,
        BigDecimal experienceTagScore,
        BigDecimal careerLevelScore,
        BigDecimal confidenceScore,
        BigDecimal requiredSkillRate,
        BigDecimal preferredSkillRate,
        BigDecimal experienceTagRate,
        BigDecimal careerLevelFitRate,
        BigDecimal confidenceRate
) {
}
