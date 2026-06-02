package jobflow.domain.application.dto;

import java.time.LocalDateTime;
import jobflow.domain.application.Application;
import jobflow.domain.application.ApplicationStatus;
import jobflow.domain.job.JobStatus;

public record ApplicationResponse(
        Long id,
        Long jobId,
        String jobTitle,
        String companyName,
        JobStatus jobStatus,
        ApplicationStatus status,
        LocalDateTime appliedAt,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getJob().getId(),
                application.getJob().getTitle(),
                application.getJob().getCompanyName(),
                application.getJob().getStatus(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getVersion(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
