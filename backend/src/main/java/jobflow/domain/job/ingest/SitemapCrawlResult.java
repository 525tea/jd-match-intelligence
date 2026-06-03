package jobflow.domain.job.ingest;

import java.util.List;

public record SitemapCrawlResult(
        JobIngestionSource source,
        int fetchedSitemapCount,
        List<String> fetchedSitemapUrls,
        List<CrawlerUrlCandidate> jobUrls
) {

    public int discoveredJobUrlCount() {
        return jobUrls.size();
    }
}
