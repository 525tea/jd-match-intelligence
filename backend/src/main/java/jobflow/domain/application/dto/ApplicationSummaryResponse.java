package jobflow.domain.application.dto;

import java.time.LocalDateTime;
import jobflow.domain.application.Application;
import jobflow.domain.application.ApplicationStatus;
import jobflow.domain.job.JobStatus;

public record ApplicationSummaryResponse(
        Long id,
        Long jobId,
        String jobTitle,
        String companyName,
        JobStatus jobStatus,
        ApplicationStatus status,
        LocalDateTime appliedAt,
        LocalDateTime updatedAt
) {

    public static ApplicationSummaryResponse from(Application application) {
        return new ApplicationSummaryResponse(
                application.getId(),
                application.getJob().getId(),
                application.getJob().getTitle(),
                application.getJob().getCompanyName(),
                application.getJob().getStatus(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getUpdatedAt()
        );
    }
}
