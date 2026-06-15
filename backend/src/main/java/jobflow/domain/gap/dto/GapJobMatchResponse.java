package jobflow.domain.gap.dto;

import java.math.BigDecimal;
import java.util.List;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;

public record GapJobMatchResponse(
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
        List<String> missingPreferredSkills,
        GapJobMatchEvidenceResponse evidence
) {

    public static GapJobMatchResponse from(
            JobSkillMatchResponse response,
            GapJobMatchEvidenceResponse evidence
    ) {
        return new GapJobMatchResponse(
                response.jobId(),
                response.title(),
                response.companyName(),
                response.role(),
                response.careerLevel(),
                response.requiredSkillCount(),
                response.matchedRequiredSkillCount(),
                response.missingRequiredSkillCount(),
                response.requiredMatchRate(),
                response.preferredSkillCount(),
                response.matchedPreferredSkillCount(),
                response.missingPreferredSkillCount(),
                response.preferredMatchRate(),
                response.matchScore(),
                response.matchedRequiredSkills(),
                response.missingRequiredSkills(),
                response.matchedPreferredSkills(),
                response.missingPreferredSkills(),
                evidence
        );
    }
}
