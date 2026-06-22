package jobflow.collector.job.backfill;

public record RawJobSnapshotBackfillSummary(
        int processedCount,
        int snapshottedCount,
        int purgedRawDataCount,
        int skippedMissingRawDataCount,
        int skippedAlreadySnapshottedCount,
        int failedCount
) {
}
