package jobflow.domain.matching.dto;

import java.math.BigDecimal;
import jobflow.domain.matching.JdMatchScoreBreakdown;

public record JdMatchScoreResponse(
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

    public static JdMatchScoreResponse from(JdMatchScoreBreakdown breakdown) {
        return new JdMatchScoreResponse(
                breakdown.totalScore(),
                breakdown.requiredSkillScore(),
                breakdown.preferredSkillScore(),
                breakdown.experienceTagScore(),
                breakdown.careerLevelScore(),
                breakdown.confidenceScore(),
                breakdown.requiredSkillRate(),
                breakdown.preferredSkillRate(),
                breakdown.experienceTagRate(),
                breakdown.careerLevelFitRate(),
                breakdown.confidenceRate()
        );
    }
}
