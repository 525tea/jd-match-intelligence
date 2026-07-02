package jobflow.domain.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "jobflow.outbox.cleanup.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class OutboxCleanupScheduler {

    private final OutboxCleanupService outboxCleanupService;

    @Scheduled(
            fixedDelayString = "${jobflow.outbox.cleanup.scheduler.fixed-delay:60000}",
            initialDelayString = "${jobflow.outbox.cleanup.scheduler.initial-delay:60000}"
    )
    public void cleanupProcessedEvents() {
        int deletedCount = outboxCleanupService.cleanupProcessedEvents();
        if (deletedCount > 0) {
            log.info("Outbox cleanup scheduler deleted processed events. deletedCount={}", deletedCount);
        }
    }
}
