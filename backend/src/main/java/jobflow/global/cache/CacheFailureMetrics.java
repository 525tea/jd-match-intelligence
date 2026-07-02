package jobflow.global.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CacheFailureMetrics {

    private static final String METRIC_NAME = "jobflow.cache.redis.error";

    private final MeterRegistry meterRegistry;

    public CacheFailureMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String operation, String cacheName) {
        meterRegistry.counter(
                METRIC_NAME,
                "operation", operation,
                "cache", cacheName
        ).increment();
    }
}
