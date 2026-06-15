package jobflow.domain.recommendation.dto;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;
import jobflow.domain.userjob.UserJobStatus;

public record JobRecommendationResponse(
        Long jobId,
        String source,
        String title,
        String companyName,
        JobRole role,
        CareerLevel careerLevel,
        EmploymentType employmentType,
        String locationRegion,
        String locationCity,
        RemoteType remoteType,
        LocalDateTime deadlineAt,
        JobStatus status,
        UserJobStatus userJobStatus,
        JobRecommendationScoreResponse score,
        long requiredSkillCount,
        long matchedRequiredSkillCount,
        long missingRequiredSkillCount,
        long preferredSkillCount,
        long matchedPreferredSkillCount,
        long missingPreferredSkillCount,
        List<String> matchedRequiredSkills,
        List<String> missingRequiredSkills,
        List<String> matchedPreferredSkills,
        List<String> missingPreferredSkills
) {

    public static JobRecommendationResponse from(
            Job job,
            JobSkillMatchResponse matchResponse,
            UserJobStatus userJobStatus,
            JobRecommendationScoreResponse score
    ) {
        return new JobRecommendationResponse(
                job.getId(),
                job.getSource(),
                job.getTitle(),
                job.getCompanyName(),
                job.getRole(),
                job.getCareerLevel(),
                job.getEmploymentType(),
                job.getLocationRegion(),
                job.getLocationCity(),
                job.getRemoteType(),
                job.getDeadlineAt(),
                job.getStatus(),
                userJobStatus,
                score,
                matchResponse.requiredSkillCount(),
                matchResponse.matchedRequiredSkillCount(),
                matchResponse.missingRequiredSkillCount(),
                matchResponse.preferredSkillCount(),
                matchResponse.matchedPreferredSkillCount(),
                matchResponse.missingPreferredSkillCount(),
                matchResponse.matchedRequiredSkills(),
                matchResponse.missingRequiredSkills(),
                matchResponse.matchedPreferredSkills(),
                matchResponse.missingPreferredSkills()
        );
    }
}
