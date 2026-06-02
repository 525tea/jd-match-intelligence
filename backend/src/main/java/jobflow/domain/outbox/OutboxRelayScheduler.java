package jobflow.domain.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
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
