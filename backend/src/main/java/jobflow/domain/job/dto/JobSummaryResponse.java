package jobflow.domain.job.dto;

import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;

public record JobSummaryResponse(
        Long id,
        String title,
        String companyName,
        JobRole role,
        CareerLevel careerLevel,
        EmploymentType employmentType,
        String locationRegion,
        String locationCity,
        RemoteType remoteType,
        LocalDateTime deadlineAt,
        JobStatus status
) {

    public static JobSummaryResponse from(Job job) {
        return new JobSummaryResponse(
                job.getId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getRole(),
                job.getCareerLevel(),
                job.getEmploymentType(),
                job.getLocationRegion(),
                job.getLocationCity(),
                job.getRemoteType(),
                job.getDeadlineAt(),
                job.getStatus()
        );
    }
}
