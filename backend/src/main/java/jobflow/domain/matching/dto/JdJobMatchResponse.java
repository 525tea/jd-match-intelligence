package jobflow.domain.matching.dto;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.analytics.JobSkillMatchDetail;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.matching.JdMatchScoreBreakdown;
import jobflow.domain.project.UserProjectAnalysis;

public record JdJobMatchResponse(
        Long userProjectId,
        Long analysisId,
        int analysisVersion,
        LocalDateTime analyzedAt,
        Long jobId,
        String title,
        String companyName,
        JobRole role,
        CareerLevel careerLevel,
        JdMatchScoreResponse score,
        long requiredSkillCount,
        long matchedRequiredSkillCount,
        long missingRequiredSkillCount,
        long preferredSkillCount,
        long matchedPreferredSkillCount,
        long missingPreferredSkillCount,
        long experienceTagCount,
        long matchedExperienceTagCount,
        long missingExperienceTagCount,
        List<String> matchedRequiredSkills,
        List<String> missingRequiredSkills,
        List<String> matchedPreferredSkills,
        List<String> missingPreferredSkills,
        List<JdMatchExperienceTagResponse> matchedExperienceTags,
        List<JdMatchExperienceTagResponse> missingExperienceTags
) {

    public static JdJobMatchResponse from(
            Long userProjectId,
            UserProjectAnalysis analysis,
            JobSkillMatchDetail skillMatchDetail,
            JdMatchScoreBreakdown scoreBreakdown,
            List<JdMatchExperienceTagResponse> matchedExperienceTags,
            List<JdMatchExperienceTagResponse> missingExperienceTags
    ) {
        var summary = skillMatchDetail.summary();

        return new JdJobMatchResponse(
                userProjectId,
                analysis.getId(),
                analysis.getAnalysisVersion(),
                analysis.getAnalyzedAt(),
                summary.jobId(),
                summary.title(),
                summary.companyName(),
                summary.role(),
                summary.careerLevel(),
                JdMatchScoreResponse.from(scoreBreakdown),
                summary.requiredSkillCount(),
                summary.matchedRequiredSkillCount(),
                summary.missingRequiredSkillCount(),
                summary.preferredSkillCount(),
                summary.matchedPreferredSkillCount(),
                summary.missingPreferredSkillCount(),
                matchedExperienceTags.size() + missingExperienceTags.size(),
                matchedExperienceTags.size(),
                missingExperienceTags.size(),
                skillMatchDetail.matchedRequiredSkills(),
                skillMatchDetail.missingRequiredSkills(),
                skillMatchDetail.matchedPreferredSkills(),
                skillMatchDetail.missingPreferredSkills(),
                matchedExperienceTags,
                missingExperienceTags
        );
    }
}
