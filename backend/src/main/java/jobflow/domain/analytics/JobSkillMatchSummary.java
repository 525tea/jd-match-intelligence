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

    public long missingRequiredSkillCount() {
        return requiredSkillCount - matchedRequiredSkillCount;
    }

    public long missingPreferredSkillCount() {
        return preferredSkillCount - matchedPreferredSkillCount;
    }

    public double requiredMatchRate() {
        if (requiredSkillCount == 0) {
            return 1.0;
        }
        return matchedRequiredSkillCount / (double) requiredSkillCount;
    }

    public double preferredMatchRate() {
        if (preferredSkillCount == 0) {
            return 1.0;
        }
        return matchedPreferredSkillCount / (double) preferredSkillCount;
    }
}
