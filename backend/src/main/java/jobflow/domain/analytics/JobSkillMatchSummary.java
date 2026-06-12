package jobflow.domain.analytics;

import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;

public record JobSkillMatchSummary(
        Long jobId,
        String title,
        String companyName,
        JobRole role,
        CareerLevel careerLevel,
        Long requiredSkillCount,
        Long matchedRequiredSkillCount,
        Long preferredSkillCount,
        Long matchedPreferredSkillCount
) {

    public JobSkillMatchSummary {
        requiredSkillCount = zeroIfNull(requiredSkillCount);
        matchedRequiredSkillCount = zeroIfNull(matchedRequiredSkillCount);
        preferredSkillCount = zeroIfNull(preferredSkillCount);
        matchedPreferredSkillCount = zeroIfNull(matchedPreferredSkillCount);
    }

    public long missingRequiredSkillCount() {
        return requiredSkillCount - matchedRequiredSkillCount;
    }

    public long missingPreferredSkillCount() {
        return preferredSkillCount - matchedPreferredSkillCount;
    }

    public Double requiredMatchRate() {
        if (requiredSkillCount == 0) {
            return null;
        }
        return matchedRequiredSkillCount / (double) requiredSkillCount;
    }

    public Double preferredMatchRate() {
        if (preferredSkillCount == 0) {
            return null;
        }
        return matchedPreferredSkillCount / (double) preferredSkillCount;
    }

    public double matchScore() {
        return matchedRequiredSkillCount * 10.0
                + requiredMatchScore()
                + matchedPreferredSkillCount * 3.0
                + preferredMatchScore()
                - missingRequiredSkillCount() * 3.0;
    }

    private double requiredMatchScore() {
        Double matchRate = requiredMatchRate();
        if (matchRate == null) {
            return 0.0;
        }
        return matchRate * 50.0;
    }

    private double preferredMatchScore() {
        Double matchRate = preferredMatchRate();
        if (matchRate == null) {
            return 0.0;
        }
        return matchRate * 10.0;
    }

    private static Long zeroIfNull(Long value) {
        if (value == null) {
            return 0L;
        }
        return value;
    }
}
