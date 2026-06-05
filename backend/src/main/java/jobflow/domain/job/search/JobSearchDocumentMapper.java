package jobflow.domain.job.search;

import jobflow.domain.job.Job;
import org.springframework.stereotype.Component;

@Component
public class JobSearchDocumentMapper {

    public JobSearchDocument toDocument(Job job) {
        return new JobSearchDocument(
                String.valueOf(job.getId()),
                job.getSource(),
                job.getExternalId(),
                job.getCanonicalFingerprint(),
                job.getTitle(),
                job.getCompanyName(),
                job.getDescription(),
                job.getRole().name(),
                job.getRoleDetail(),
                job.getCareerLevel().name(),
                job.getEmploymentType().name(),
                job.getIndustry(),
                job.getLocationCountry(),
                job.getLocationRegion(),
                job.getLocationCity(),
                job.getRemoteType().name(),
                job.getDeadlineAt(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
