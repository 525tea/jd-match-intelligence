package jobflow.domain.job.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search.elasticsearch")
public record JobSearchProperties(
        String url,
        String indexName,
        String physicalIndexName,
        boolean initializeOnStartup
) {
}
