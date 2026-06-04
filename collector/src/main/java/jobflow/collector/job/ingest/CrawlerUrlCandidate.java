package jobflow.collector.job.ingest;

public record CrawlerUrlCandidate(
        JobIngestionSource source,
        String sourceUrl,
        String detailUrl,
        String externalId
) {
}
