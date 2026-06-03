package jobflow.domain.job.ingest;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.crawler")
public record CrawlerProperties(
        String userAgent,
        Duration defaultRequestDelay,
        int defaultDailyMaxRequests,
        Map<JobIngestionSource, SourceProperties> sources
) {

    public CrawlerSourcePolicy policy(JobIngestionSource source) {
        SourceProperties sourceProperties = sources.get(source);

        if (sourceProperties == null) {
            throw new IllegalArgumentException("Crawler source policy not found. source=" + source);
        }

        return new CrawlerSourcePolicy(
                source,
                sourceProperties.baseUrl(),
                sourceProperties.robotsUrl(),
                sourceProperties.sitemapUrl(),
                sourceProperties.allowedPathPrefixes(),
                sourceProperties.disallowedPathPrefixes(),
                sourceProperties.requestDelay() == null ? defaultRequestDelay : sourceProperties.requestDelay(),
                sourceProperties.dailyMaxRequests() == null ? defaultDailyMaxRequests : sourceProperties.dailyMaxRequests()
        );
    }

    public Map<JobIngestionSource, CrawlerSourcePolicy> policies() {
        Map<JobIngestionSource, CrawlerSourcePolicy> policies = new EnumMap<>(JobIngestionSource.class);

        sources.keySet()
                .forEach(source -> policies.put(source, policy(source)));

        return policies;
    }

    public record SourceProperties(
            String baseUrl,
            String robotsUrl,
            String sitemapUrl,
            List<String> allowedPathPrefixes,
            List<String> disallowedPathPrefixes,
            Duration requestDelay,
            Integer dailyMaxRequests
    ) {
    }
}
