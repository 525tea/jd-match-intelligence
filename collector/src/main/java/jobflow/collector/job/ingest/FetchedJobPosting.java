package jobflow.collector.job.ingest;

public record FetchedJobPosting(
        JobIngestionSource source,
        String externalId,
        String sourceUrl,
        String detailUrl,
        String body
) {
}
