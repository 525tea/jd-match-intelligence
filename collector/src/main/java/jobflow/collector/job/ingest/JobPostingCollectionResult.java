package jobflow.collector.job.ingest;

public record JobPostingCollectionResult(
        CrawlerUrlCandidate candidate,
        JobIngestionResultType ingestionResultType
) {
}
