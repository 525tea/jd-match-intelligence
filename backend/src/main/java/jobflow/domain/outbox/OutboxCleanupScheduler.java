package jobflow.domain.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OutboxCleanupScheduler {

    private final OutboxCleanupService outboxCleanupService;
    private final boolean enabled;

    public OutboxCleanupScheduler(
            OutboxCleanupService outboxCleanupService,
            @Value("${jobflow.outbox.cleanup.scheduler.enabled:false}") boolean enabled
    ) {
        this.outboxCleanupService = outboxCleanupService;
        this.enabled = enabled;
        log.info("Outbox cleanup scheduler configured. enabled={}", enabled);
    }

    @Scheduled(
            fixedDelayString = "${jobflow.outbox.cleanup.scheduler.fixed-delay:60000}",
            initialDelayString = "${jobflow.outbox.cleanup.scheduler.initial-delay:60000}"
    )
    public void cleanupProcessedEvents() {
        if (!enabled) {
            return;
        }

        int deletedCount = outboxCleanupService.cleanupProcessedEvents();
        if (deletedCount > 0) {
            log.info("Outbox cleanup scheduler deleted processed events. deletedCount={}", deletedCount);
        }
    }
}
