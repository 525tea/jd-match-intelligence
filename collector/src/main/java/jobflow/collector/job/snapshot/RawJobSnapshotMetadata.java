package jobflow.collector.job.snapshot;

import java.time.LocalDateTime;

public record RawJobSnapshotMetadata(
        String key,
        String hash,
        long sizeBytes,
        String storageType,
        LocalDateTime savedAt
) {
}
