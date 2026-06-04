package jobflow.collector.job.ingest;

import jobflow.collector.job.Job;
import org.springframework.stereotype.Component;

@Component
public class IngestedJobMapper {

    public Job toJob(IngestedJobPosting posting) {
        Job job = Job.create(
                posting.source().name(),
                posting.externalId(),
                posting.title(),
                posting.companyName(),
                posting.description(),
                posting.detailUrl(),
                posting.role(),
                posting.roleDetail(),
                posting.careerLevel(),
                posting.minExperienceYears(),
                posting.maxExperienceYears(),
                posting.educationLevel(),
                posting.employmentType(),
                posting.companySize(),
                posting.industry(),
                posting.locationCountry(),
                posting.locationRegion(),
                posting.locationCity(),
                posting.remoteType(),
                posting.salaryMin(),
                posting.salaryMax(),
                posting.salaryCurrency(),
                posting.salaryVisible(),
                posting.hiringCount(),
                posting.openedAt(),
                posting.deadlineAt()
        );

        job.updateCrawlingMetadata(
                null,
                posting.sourceUrl(),
                posting.collectedAt(),
                posting.lastSeenAt(),
                posting.sourceUpdatedAt(),
                posting.rawData(),
                posting.crawlerVersion()
        );

        return job;
    }
}
