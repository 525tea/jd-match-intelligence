package jobflow.domain.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    private static final long SNAPSHOT_TTL_MILLIS = 1_000L;

    private final OutboxEventRepository outboxEventRepository;
    private final AtomicReference<OutboxStatusSnapshot> snapshot = new AtomicReference<>(
            new OutboxStatusSnapshot(0L, Map.of())
    );

    public OutboxMetrics(
            MeterRegistry meterRegistry,
            OutboxEventRepository outboxEventRepository
    ) {
        this.outboxEventRepository = outboxEventRepository;

        for (OutboxStatus status : OutboxStatus.values()) {
            Gauge.builder("jobflow.outbox.events", this, metrics -> metrics.countByStatus(status))
                    .description("Current number of outbox events by status")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(meterRegistry);
        }
    }

    private long countByStatus(OutboxStatus status) {
        return currentCounts().getOrDefault(status, 0L);
    }

    private Map<OutboxStatus, Long> currentCounts() {
        OutboxStatusSnapshot current = snapshot.get();
        long now = System.currentTimeMillis();
        if (now - current.refreshedAtMillis() < SNAPSHOT_TTL_MILLIS) {
            return current.counts();
        }

        Map<OutboxStatus, Long> counts = new EnumMap<>(OutboxStatus.class);
        for (OutboxStatusCount statusCount : outboxEventRepository.countGroupByStatus()) {
            counts.put(statusCount.getStatus(), statusCount.getCount());
        }
        OutboxStatusSnapshot refreshed = new OutboxStatusSnapshot(now, Map.copyOf(counts));
        snapshot.set(refreshed);
        return refreshed.counts();
    }

    private record OutboxStatusSnapshot(
            long refreshedAtMillis,
            Map<OutboxStatus, Long> counts
    ) {
    }
}
