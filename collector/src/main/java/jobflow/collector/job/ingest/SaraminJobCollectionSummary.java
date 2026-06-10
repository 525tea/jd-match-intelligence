package jobflow.collector.job.ingest;

public record SaraminJobCollectionSummary(
        int processedCount,
        int collectedCount,
        int createdCount,
        int updatedCount,
        int failedCount
) {
}
