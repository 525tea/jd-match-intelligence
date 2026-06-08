package jobflow.collector.job.ingest;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CrawlerRequestThrottle {

    private final CrawlerProperties crawlerProperties;
    private final Map<JobIngestionSource, Instant> lastRequestedAt =
            new EnumMap<>(JobIngestionSource.class);

    public CrawlerRequestThrottle(CrawlerProperties crawlerProperties) {
        this.crawlerProperties = crawlerProperties;
    }

    public synchronized void waitUntilAllowed(JobIngestionSource source) {
        CrawlerSourcePolicy policy = crawlerProperties.policy(source);
        Duration requestDelay = policy.requestDelay();
        Instant now = Instant.now();
        Instant lastRequestAt = lastRequestedAt.get(source);

        if (lastRequestAt != null) {
            Duration elapsed = Duration.between(lastRequestAt, now);
            Duration remainingDelay = requestDelay.minus(elapsed);

            if (!remainingDelay.isNegative() && !remainingDelay.isZero()) {
                sleep(remainingDelay);
                now = Instant.now();
            }
        }

        lastRequestedAt.put(source, now);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crawler request throttle was interrupted", exception);
        }
    }
}
