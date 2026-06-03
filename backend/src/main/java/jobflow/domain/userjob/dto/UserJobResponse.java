package jobflow.domain.userjob.dto;

import jobflow.domain.userjob.UserJob;
import jobflow.domain.userjob.UserJobStatus;

import java.time.LocalDateTime;

public record UserJobResponse(
        Long id,
        Long userId,
        Long jobId,
        String jobTitle,
        String companyName,
        UserJobStatus status,
        LocalDateTime viewedAt,
        LocalDateTime savedAt,
        LocalDateTime ignoredAt
) {
    public static UserJobResponse from(UserJob userJob) {
        return new UserJobResponse(
                userJob.getId(),
                userJob.getUser().getId(),
                userJob.getJob().getId(),
                userJob.getJob().getTitle(),
                userJob.getJob().getCompanyName(),
                userJob.getStatus(),
                userJob.getViewedAt(),
                userJob.getSavedAt(),
                userJob.getIgnoredAt()
        );
    }
}
