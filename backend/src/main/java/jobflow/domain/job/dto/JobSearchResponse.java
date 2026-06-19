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
        JobStatus status,
        Double score
) {
}
