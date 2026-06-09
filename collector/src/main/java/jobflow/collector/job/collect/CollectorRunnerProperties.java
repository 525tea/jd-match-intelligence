package jobflow.collector.job.collect;

import jobflow.collector.job.ingest.JobIngestionSource;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.collector")
public record CollectorRunnerProperties(
        boolean enabled,
        JobIngestionSource source,
        int previewLimit,
        int collectLimit,
        int scanLimit
) {

    private static final JobIngestionSource DEFAULT_SOURCE = JobIngestionSource.ZIGHANG;
    private static final int DEFAULT_PREVIEW_LIMIT = 5;
    private static final int DEFAULT_COLLECT_LIMIT = 10;
    private static final int DEFAULT_SCAN_LIMIT = 100;

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

    public int collectLimitOrDefault() {
        if (collectLimit <= 0) {
            return DEFAULT_COLLECT_LIMIT;
        }

        return collectLimit;
    }

    public int scanLimitOrDefault() {
        if (scanLimit <= 0) {
            return Math.max(DEFAULT_SCAN_LIMIT, collectLimitOrDefault());
        }

        return Math.max(scanLimit, collectLimitOrDefault());
    }
}
