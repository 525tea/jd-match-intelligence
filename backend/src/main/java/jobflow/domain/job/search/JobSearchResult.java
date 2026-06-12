package jobflow.domain.job.search;

import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobSearchProjection;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;
import jobflow.domain.job.dto.JobSearchResponse;

public record JobSearchResult(
        Long id,
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
        Double score
) {

    public static JobSearchResult fromProjection(JobSearchProjection projection) {
        return new JobSearchResult(
                projection.getId(),
                projection.getSource(),
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

    public static JobSearchResult fromDocument(JobSearchDocument document, Double score) {
        return new JobSearchResult(
                Long.valueOf(document.id()),
                document.source(),
                document.title(),
                document.companyName(),
                JobRole.valueOf(document.role()),
                CareerLevel.valueOf(document.careerLevel()),
                EmploymentType.valueOf(document.employmentType()),
                document.locationRegion(),
                document.locationCity(),
                RemoteType.valueOf(document.remoteType()),
                document.deadlineAt(),
                JobStatus.OPEN,
                score
        );
    }

    public JobSearchResponse toResponse() {
        return new JobSearchResponse(
                id,
                source,
                title,
                companyName,
                role,
                careerLevel,
                employmentType,
                locationRegion,
                locationCity,
                remoteType,
                deadlineAt,
                status,
                score
        );
    }
}
