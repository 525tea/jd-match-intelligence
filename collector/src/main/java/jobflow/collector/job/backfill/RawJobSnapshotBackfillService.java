package jobflow.collector.job.backfill;

import java.util.List;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRepository;
import jobflow.collector.job.ingest.JobIngestionSource;
import jobflow.collector.job.snapshot.RawJobSnapshotMetadata;
import jobflow.collector.job.snapshot.RawJobSnapshotStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RawJobSnapshotBackfillService {

    private final JobRepository jobRepository;
    private final RawJobSnapshotStorage rawJobSnapshotStorage;

    @Transactional
    public RawJobSnapshotBackfillSummary backfill(List<String> sources) {
        List<Job> jobs = jobRepository.findBySourceInOrderByIdAsc(sources);

        int snapshottedCount = 0;
        int skippedMissingRawDataCount = 0;
        int skippedAlreadySnapshottedCount = 0;
        int failedCount = 0;

        for (Job job : jobs) {
            if (job.getRawSnapshotKey() != null && !job.getRawSnapshotKey().isBlank()) {
                skippedAlreadySnapshottedCount++;
                continue;
            }

            if (job.getRawData() == null || job.getRawData().isBlank()) {
                skippedMissingRawDataCount++;
                continue;
            }

            try {
                JobIngestionSource source = JobIngestionSource.valueOf(job.getSource());
                RawJobSnapshotMetadata metadata = rawJobSnapshotStorage.save(
                        source,
                        job.getExternalId(),
                        job.getRawData()
                );

                job.updateRawSnapshotMetadata(
                        metadata.key(),
                        metadata.hash(),
                        metadata.sizeBytes(),
                        metadata.storageType(),
                        metadata.savedAt()
                );
                snapshottedCount++;
            } catch (IllegalArgumentException exception) {
                failedCount++;
                log.warn(
                        "Raw job snapshot backfill skipped. reason=unsupported_source, jobId={}, source={}, externalId={}",
                        job.getId(),
                        job.getSource(),
                        job.getExternalId()
                );
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn(
                        "Raw job snapshot backfill failed. jobId={}, source={}, externalId={}, error={}",
                        job.getId(),
                        job.getSource(),
                        job.getExternalId(),
                        exception.getMessage()
                );
            }
        }

        return new RawJobSnapshotBackfillSummary(
                jobs.size(),
                snapshottedCount,
                skippedMissingRawDataCount,
                skippedAlreadySnapshottedCount,
                failedCount
        );
    }
}
