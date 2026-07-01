package jobflow.domain.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxKafkaMessageParser {

    private final ObjectMapper objectMapper;

    public OutboxKafkaEnvelope parseEnvelope(String message) {
        JsonNode root = parseRoot(message);
        JsonNode payload = payloadOrRoot(root);

        return new OutboxKafkaEnvelope(
                intOrDefault(root, "schemaVersion", 1),
                longOrNull(root, "eventId"),
                textOrNull(root, "aggregateType"),
                longOrNull(root, "aggregateId"),
                textOrNull(root, "eventType"),
                textOrNull(root, "topic"),
                payload
        );
    }

    public JsonNode parsePayloadOrRoot(String message) {
        return payloadOrRoot(parseRoot(message));
    }

    private JsonNode parseRoot(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Kafka message is not valid JSON", e);
        }
    }

    private JsonNode payloadOrRoot(JsonNode root) {
        JsonNode payload = root.path("payload");
        if (payload.isMissingNode() || payload.isNull()) {
            return root;
        }
        return payload;
    }

    private Long longOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.canConvertToLong()) {
            throw new IllegalArgumentException("Kafka message field '%s' must be a long".formatted(fieldName));
        }
        return value.longValue();
    }

    private int intOrDefault(JsonNode node, String fieldName, int defaultValue) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        if (!value.canConvertToInt()) {
            throw new IllegalArgumentException("Kafka message field '%s' must be an integer".formatted(fieldName));
        }
        return value.intValue();
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Kafka message field '%s' must be a string".formatted(fieldName));
        }
        return value.textValue();
    }
}
