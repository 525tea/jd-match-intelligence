package jobflow.domain.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "jobflow.outbox.relay.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxRelayScheduler {

    private final OutboxRelayService outboxRelayService;

    @Scheduled(
            fixedDelayString = "${jobflow.outbox.relay.fixed-delay:5000}",
            initialDelayString = "${jobflow.outbox.relay.initial-delay:5000}"
    )
    public void relayPendingEvents() {
        outboxRelayService.relayPendingEvents();
    }
}
