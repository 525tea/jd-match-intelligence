package jobflow.collector.job.collect;

import jobflow.collector.job.ingest.JobIngestionSource;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.collector")
public record CollectorRunnerProperties(
        boolean enabled,
        JobIngestionSource source,
        int previewLimit
) {

    private static final JobIngestionSource DEFAULT_SOURCE = JobIngestionSource.ZIGHANG;
    private static final int DEFAULT_PREVIEW_LIMIT = 5;

    public JobIngestionSource sourceOrDefault() {
        if (source == null) {
            return DEFAULT_SOURCE;
        }

        return source;
    }

    public int previewLimitOrDefault() {
        if (previewLimit <= 0) {
            return DEFAULT_PREVIEW_LIMIT;
        }

        return previewLimit;
    }
}
