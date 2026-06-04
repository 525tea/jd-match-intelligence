package jobflow.collector.job.ingest;

import java.time.Duration;
import java.util.List;

public record CrawlerSourcePolicy(
        JobIngestionSource source,
        String baseUrl,
        String robotsUrl,
        String sitemapUrl,
        List<String> allowedPathPrefixes,
        List<String> disallowedPathPrefixes,
        Duration requestDelay,
        int dailyMaxRequests
) {

    public boolean isAllowedPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        boolean disallowed = disallowedPathPrefixes.stream()
                .anyMatch(path::startsWith);

        if (disallowed) {
            return false;
        }

        return allowedPathPrefixes.stream()
                .anyMatch(path::startsWith);
    }
}
