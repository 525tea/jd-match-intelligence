package jobflow.domain.job.dto;

import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobSearchProjection;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;

public record JobSearchResponse(
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
        JobStatus status,
        Double score
) {

    public static JobSearchResponse from(JobSearchProjection projection) {
        return new JobSearchResponse(
                projection.getId(),
                projection.getTitle(),
                projection.getCompanyName(),
                JobRole.valueOf(projection.getRole()),
                CareerLevel.valueOf(projection.getCareerLevel()),
                EmploymentType.valueOf(projection.getEmploymentType()),
                projection.getLocationRegion(),
                projection.getLocationCity(),
                RemoteType.valueOf(projection.getRemoteType()),
                projection.getDeadlineAt(),
                JobStatus.valueOf(projection.getStatus()),
                projection.getScore()
        );
    }
}
