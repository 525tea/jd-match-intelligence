package jobflow.collector.job.ingest;

import jobflow.collector.job.Job;
import jobflow.collector.job.JobRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IngestedJobMapper {

    private final CanonicalFingerprintGenerator canonicalFingerprintGenerator;
    private final JdJobRoleClassificationService jdJobRoleClassificationService;

    public Job toJob(IngestedJobPosting posting) {
        JobRole resolvedRole = jdJobRoleClassificationService.resolve(
                posting.role(),
                posting.title(),
                posting.description(),
                posting.roleDetail()
        );

        Job job = Job.create(
                posting.source().name(),
                posting.externalId(),
                posting.title(),
                posting.companyName(),
                posting.description(),
                posting.detailUrl(),
                resolvedRole,
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
                canonicalFingerprintGenerator.generate(posting),
                posting.sourceUrl(),
                posting.collectedAt(),
                posting.lastSeenAt(),
                posting.sourceUpdatedAt(),
                posting.rawData(),
                posting.crawlerVersion()
        );
        job.updateDescriptionSections(posting.descriptionSections());

        return job;
    }
}
