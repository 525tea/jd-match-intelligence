package jobflow.domain.analytics.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import jobflow.domain.analytics.JobSkillMatchSummary;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;

public record JobSkillMatchResponse(
        Long jobId,
        String title,
        String companyName,
        JobRole role,
        CareerLevel careerLevel,
        long requiredSkillCount,
        long matchedRequiredSkillCount,
        long missingRequiredSkillCount,
        BigDecimal requiredMatchRate,
        long preferredSkillCount,
        long matchedPreferredSkillCount,
        long missingPreferredSkillCount,
        BigDecimal preferredMatchRate,
        BigDecimal matchScore
) {

    public static JobSkillMatchResponse from(JobSkillMatchSummary summary) {
        return new JobSkillMatchResponse(
                summary.jobId(),
                summary.title(),
                summary.companyName(),
                summary.role(),
                summary.careerLevel(),
                summary.requiredSkillCount(),
                summary.matchedRequiredSkillCount(),
                summary.missingRequiredSkillCount(),
                toPercent(summary.requiredMatchRate()),
                summary.preferredSkillCount(),
                summary.matchedPreferredSkillCount(),
                summary.missingPreferredSkillCount(),
                toPercent(summary.preferredMatchRate()),
                BigDecimal.valueOf(summary.matchScore()).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private static BigDecimal toPercent(Double rate) {
        if (rate == null) {
            return null;
        }
        return BigDecimal.valueOf(rate * 100.0).setScale(2, RoundingMode.HALF_UP);
    }
}
