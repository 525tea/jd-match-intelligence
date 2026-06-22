package jobflow.collector.job.snapshot;

import jobflow.collector.job.ingest.JobIngestionSource;

public interface RawJobSnapshotStorage {

    RawJobSnapshotMetadata save(
            JobIngestionSource source,
            String externalId,
            String rawData
    );

    String read(String key);
}
