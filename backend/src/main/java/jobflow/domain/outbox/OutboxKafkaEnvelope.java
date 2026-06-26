package jobflow.domain.outbox;

import tools.jackson.databind.JsonNode;

public record OutboxKafkaEnvelope(
        Long eventId,
        String aggregateType,
        Long aggregateId,
        String eventType,
        String topic,
        JsonNode payload
) {
}
