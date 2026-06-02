package jobflow.domain.outbox;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    static final int MAX_RETRY_COUNT = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final List<OutboxEventHandler> outboxEventHandlers;

    @Transactional
    public int relayPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop100ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        MAX_RETRY_COUNT
                );

        events.forEach(this::relay);

        return events.size();
    }

    private void relay(OutboxEvent event) {
        try {
            OutboxEventHandler handler = findHandler(event);
            handler.handle(event);
            event.markPublished();
        } catch (Exception exception) {
            event.markFailed(toLastError(exception), MAX_RETRY_COUNT);
        }
    }

    private OutboxEventHandler findHandler(OutboxEvent event) {
        return outboxEventHandlers.stream()
                .filter(handler -> handler.supports(event))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No outbox event handler found. eventType=" + event.getEventType()
                ));
    }

    private String toLastError(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }
}
