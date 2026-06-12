package jobflow.domain.analytics.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import jobflow.domain.analytics.JobSkillMatchDetail;
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
        BigDecimal matchScore,
        List<String> matchedRequiredSkills,
        List<String> missingRequiredSkills,
        List<String> matchedPreferredSkills,
        List<String> missingPreferredSkills
) {

    public static JobSkillMatchResponse from(JobSkillMatchDetail detail) {
        var summary = detail.summary();
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
                BigDecimal.valueOf(summary.matchScore()).setScale(2, RoundingMode.HALF_UP),
                detail.matchedRequiredSkills(),
                detail.missingRequiredSkills(),
                detail.matchedPreferredSkills(),
                detail.missingPreferredSkills()
        );
    }

    private static BigDecimal toPercent(Double rate) {
        if (rate == null) {
            return null;
        }
        return BigDecimal.valueOf(rate * 100.0).setScale(2, RoundingMode.HALF_UP);
    }
}
