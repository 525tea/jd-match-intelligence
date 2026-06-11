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
        Long matchedPreferredSkillCount,
        Double matchScore
) {

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
}
