package jobflow.collector.job.ingest;

import java.util.List;
import java.time.LocalDateTime;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRepository;
import jobflow.collector.job.snapshot.RawJobSnapshotMetadata;
import jobflow.collector.job.snapshot.RawJobSnapshotStorage;
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
    private final JobSkillNormalizationService jobSkillNormalizationService;
    private final JobExperienceTagNormalizationService jobExperienceTagNormalizationService;
    private final RawJobSnapshotStorage rawJobSnapshotStorage;

    @Transactional
    public JobIngestionResult ingest(IngestedJobPosting posting) {
        String source = posting.source().name();

        return jobRepository.findBySourceAndExternalId(source, posting.externalId())
                .map(existingJob -> update(existingJob, posting))
                .orElseGet(() -> create(posting));
    }

    private JobIngestionResult create(IngestedJobPosting posting) {
        Job job = ingestedJobMapper.toJob(posting);
        saveRawSnapshotMetadata(job, posting);

        Job savedJob = jobRepository.save(job);
        saveNormalizedSkills(savedJob);
        saveNormalizedExperienceTags(savedJob);

        outboxEventService.save(
                "JOB",
                savedJob.getId(),
                OutboxEventTypes.JOB_CREATED,
                JobOutboxPayload.from(savedJob),
                OutboxEvent.TOPIC_JOB_EVENTS
        );

        List<Job> duplicateCandidates = findDuplicateCandidates(savedJob);

        return new JobIngestionResult(JobIngestionResultType.CREATED, savedJob, duplicateCandidates);
    }

    private JobIngestionResult update(Job existingJob, IngestedJobPosting posting) {
        Job crawledJob = ingestedJobMapper.toJob(posting);
        saveRawSnapshotMetadata(crawledJob, posting);

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

        existingJob.updateRawSnapshotMetadata(
                crawledJob.getRawSnapshotKey(),
                crawledJob.getRawSnapshotHash(),
                crawledJob.getRawSnapshotSizeBytes(),
                crawledJob.getRawSnapshotStorageType(),
                crawledJob.getRawSnapshotSavedAt()
        );
        existingJob.updateDescriptionSections(crawledJob.getDescriptionSections());

        saveNormalizedSkills(existingJob);
        saveNormalizedExperienceTags(existingJob);

        outboxEventService.save(
                "JOB",
                existingJob.getId(),
                OutboxEventTypes.JOB_UPDATED,
                JobOutboxPayload.from(existingJob),
                OutboxEvent.TOPIC_JOB_EVENTS
        );

        List<Job> duplicateCandidates = findDuplicateCandidates(existingJob);

        return new JobIngestionResult(JobIngestionResultType.UPDATED, existingJob, duplicateCandidates);
    }

    private void saveNormalizedSkills(Job job) {
        jobSkillNormalizationService.replaceNormalizedSkills(
                job,
                job.getTitle(),
                job.getDescription(),
                job.getRoleDetail()
        );
    }

    private void saveNormalizedExperienceTags(Job job) {
        jobExperienceTagNormalizationService.replaceNormalizedExperienceTags(
                job,
                job.getTitle(),
                job.getDescription(),
                job.getRoleDetail()
        );
    }

    private LocalDateTime preserveCollectedAt(Job existingJob, Job crawledJob) {
        if (existingJob.getCollectedAt() != null) {
            return existingJob.getCollectedAt();
        }

        return crawledJob.getCollectedAt();
    }

    private List<Job> findDuplicateCandidates(Job job) {
        return jobRepository.findByCanonicalFingerprintAndSourceNot(
                job.getCanonicalFingerprint(),
                job.getSource()
        );
    }

    private void saveRawSnapshotMetadata(Job job, IngestedJobPosting posting) {
        RawJobSnapshotMetadata metadata = rawJobSnapshotStorage.save(
                posting.source(),
                posting.externalId(),
                posting.rawData()
        );

        job.updateRawSnapshotMetadata(
                metadata.key(),
                metadata.hash(),
                metadata.sizeBytes(),
                metadata.storageType(),
                metadata.savedAt()
        );
    }
}
