package jobflow.domain.outbox;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxRelayService {

    static final int MAX_RETRY_COUNT = 3;
    static final int DEFAULT_BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final List<OutboxEventHandler> outboxEventHandlers;
    private final int maxRetryCount;
    private final int batchSize;

    public OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            List<OutboxEventHandler> outboxEventHandlers,
            @Value("${jobflow.outbox.relay.max-retry-count:" + MAX_RETRY_COUNT + "}") int maxRetryCount,
            @Value("${jobflow.outbox.relay.batch-size:" + DEFAULT_BATCH_SIZE + "}") int batchSize
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventHandlers = outboxEventHandlers;
        this.maxRetryCount = maxRetryCount;
        this.batchSize = batchSize;
    }

    @Transactional
    public int relayPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findRelayBatch(
                OutboxStatus.PENDING,
                maxRetryCount,
                PageRequest.of(0, batchSize)
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
            event.markFailed(toLastError(exception), maxRetryCount);
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
