package jobflow.collector.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void save(
            String aggregateType,
            Long aggregateId,
            String eventType,
            Object payload,
            String topic
    ) {
        String payloadJson = serialize(payload);

        OutboxEvent event = OutboxEvent.create(
                aggregateType,
                aggregateId,
                eventType,
                payloadJson,
                topic
        );

        outboxEventRepository.save(event);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize outbox payload.", exception);
        }
    }
}
