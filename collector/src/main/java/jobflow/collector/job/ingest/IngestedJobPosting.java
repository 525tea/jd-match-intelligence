package jobflow.collector.job.ingest;

import java.time.LocalDateTime;
import jobflow.collector.job.CareerLevel;
import jobflow.collector.job.EmploymentType;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.RemoteType;

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
