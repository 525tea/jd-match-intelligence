package jobflow.domain.job.ingest;

import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;

public record IngestedJobPosting(
        JobIngestionSource source,
        String externalId,
        String title,
        String companyName,
        String description,
        String sourceUrl,
        String detailUrl,
        JobRole role,
        String roleDetail,
        CareerLevel careerLevel,
        Integer minExperienceYears,
        Integer maxExperienceYears,
        String educationLevel,
        EmploymentType employmentType,
        String companySize,
        String industry,
        String locationCountry,
        String locationRegion,
        String locationCity,
        RemoteType remoteType,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        boolean salaryVisible,
        Integer hiringCount,
        LocalDateTime openedAt,
        LocalDateTime deadlineAt,
        LocalDateTime collectedAt,
        LocalDateTime lastSeenAt,
        LocalDateTime sourceUpdatedAt,
        String rawData,
        String crawlerVersion
) {
}
