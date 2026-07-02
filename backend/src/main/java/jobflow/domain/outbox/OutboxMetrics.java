package jobflow.domain.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    private final OutboxEventRepository outboxEventRepository;

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
        return outboxEventRepository.countByStatus(status);
    }
}
