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
        String source,
        String externalId,
        String canonicalFingerprint,
        String title,
        String companyName,
        String applyUrl,
        JobRole role,
        CareerLevel careerLevel,
        EmploymentType employmentType,
        String locationRegion,
        String locationCity,
        RemoteType remoteType,
        LocalDateTime deadlineAt,
        JobStatus status
) {

    public static JobSummaryResponse from(Job job, String applyUrl) {
        return new JobSummaryResponse(
                job.getId(),
                job.getSource(),
                job.getExternalId(),
                job.getCanonicalFingerprint(),
                job.getTitle(),
                job.getCompanyName(),
                applyUrl,
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
