package jobflow.collector.job.ingest;

import java.util.List;

public record SitemapDiscoveryResult(
        List<String> sitemapUrls,
        List<CrawlerUrlCandidate> jobUrls
) {

    public boolean hasNestedSitemaps() {
        return !sitemapUrls.isEmpty();
    }

    public boolean hasJobUrls() {
        return !jobUrls.isEmpty();
    }
}
