package jobflow.domain.job.search;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class JobSearchMetrics {

    private final Counter elasticsearchFallbackCounter;

    public JobSearchMetrics(MeterRegistry meterRegistry) {
        this.elasticsearchFallbackCounter = Counter.builder("jobflow.search.fallback")
                .description("Number of job search requests served by MySQL FULLTEXT after Elasticsearch failure")
                .tag("source", "elasticsearch")
                .tag("target", "mysql_fulltext")
                .tag("reason", "elasticsearch_error")
                .register(meterRegistry);
    }

    public void recordElasticsearchFallback() {
        elasticsearchFallbackCounter.increment();
    }
}
