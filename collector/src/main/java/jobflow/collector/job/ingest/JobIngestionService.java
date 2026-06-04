package jobflow.collector.job.ingest;

import java.time.LocalDateTime;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRepository;
import jobflow.collector.outbox.OutboxEvent;
import jobflow.collector.outbox.OutboxEventService;
import jobflow.collector.outbox.OutboxEventTypes;
import jobflow.collector.outbox.payload.JobOutboxPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobIngestionService {

    private final JobRepository jobRepository;
    private final IngestedJobMapper ingestedJobMapper;
    private final OutboxEventService outboxEventService;

    @Transactional
    public JobIngestionResult ingest(IngestedJobPosting posting) {
        String source = posting.source().name();

        return jobRepository.findBySourceAndExternalId(source, posting.externalId())
                .map(existingJob -> update(existingJob, posting))
                .orElseGet(() -> create(posting));
    }

    private JobIngestionResult create(IngestedJobPosting posting) {
        Job job = ingestedJobMapper.toJob(posting);
        Job savedJob = jobRepository.save(job);

        outboxEventService.save(
                "JOB",
                savedJob.getId(),
                OutboxEventTypes.JOB_CREATED,
                JobOutboxPayload.from(savedJob),
                OutboxEvent.TOPIC_JOB_EVENTS
        );

        return new JobIngestionResult(JobIngestionResultType.CREATED, savedJob);
    }

    private JobIngestionResult update(Job existingJob, IngestedJobPosting posting) {
        Job crawledJob = ingestedJobMapper.toJob(posting);

        existingJob.update(
                crawledJob.getTitle(),
                crawledJob.getCompanyName(),
                crawledJob.getDescription(),
                crawledJob.getUrl(),
                crawledJob.getRole(),
                crawledJob.getRoleDetail(),
                crawledJob.getCareerLevel(),
                crawledJob.getMinExperienceYears(),
                crawledJob.getMaxExperienceYears(),
                crawledJob.getEducationLevel(),
                crawledJob.getEmploymentType(),
                crawledJob.getCompanySize(),
                crawledJob.getIndustry(),
                crawledJob.getLocationCountry(),
                crawledJob.getLocationRegion(),
                crawledJob.getLocationCity(),
                crawledJob.getRemoteType(),
                crawledJob.getSalaryMin(),
                crawledJob.getSalaryMax(),
                crawledJob.getSalaryCurrency(),
                crawledJob.isSalaryVisible(),
                crawledJob.getHiringCount(),
                crawledJob.getOpenedAt(),
                crawledJob.getDeadlineAt()
        );

        existingJob.updateCrawlingMetadata(
                crawledJob.getCanonicalFingerprint(),
                crawledJob.getOriginalUrl(),
                preserveCollectedAt(existingJob, crawledJob),
                crawledJob.getLastSeenAt(),
                crawledJob.getSourceUpdatedAt(),
                crawledJob.getRawData(),
                crawledJob.getCrawlerVersion()
        );

        outboxEventService.save(
                "JOB",
                existingJob.getId(),
                OutboxEventTypes.JOB_UPDATED,
                JobOutboxPayload.from(existingJob),
                OutboxEvent.TOPIC_JOB_EVENTS
        );

        return new JobIngestionResult(JobIngestionResultType.UPDATED, existingJob);
    }

    private LocalDateTime preserveCollectedAt(Job existingJob, Job crawledJob) {
        if (existingJob.getCollectedAt() != null) {
            return existingJob.getCollectedAt();
        }

        return crawledJob.getCollectedAt();
    }
}
