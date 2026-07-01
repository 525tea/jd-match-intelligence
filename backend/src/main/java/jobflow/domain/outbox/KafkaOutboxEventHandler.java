package jobflow.domain.outbox;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@ConditionalOnProperty(name = "jobflow.outbox.relay.publisher", havingValue = "kafka")
public class KafkaOutboxEventHandler implements OutboxEventHandler {

    static final int OUTBOX_ENVELOPE_SCHEMA_VERSION = 1;
    private static final String LEGACY_JOB_EVENTS_TOPIC = "job.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final long sendTimeoutMillis;

    public KafkaOutboxEventHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${jobflow.outbox.relay.kafka.send-timeout-millis:3000}") long sendTimeoutMillis
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    @Override
    public boolean supports(OutboxEvent event) {
        return event.getTopic() != null && !event.getTopic().isBlank();
    }

    @Override
    public void handle(OutboxEvent event) {
        String topic = resolveTopic(event);
        String key = event.getAggregateType() + ":" + event.getAggregateId();
        String message = buildMessage(event, topic);

        try {
            kafkaTemplate.send(topic, key, message).get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event to Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish outbox event to Kafka", e);
        }

        log.info(
                "Outbox event published to Kafka. eventId={}, topic={}, eventType={}, aggregateType={}, aggregateId={}",
                event.getId(),
                topic,
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId()
        );
    }

    private String resolveTopic(OutboxEvent event) {
        if (LEGACY_JOB_EVENTS_TOPIC.equals(event.getTopic())) {
            return OutboxEvent.TOPIC_JOB_EVENTS;
        }
        return event.getTopic();
    }

    private String buildMessage(OutboxEvent event, String topic) {
        ObjectNode root = objectMapper.createObjectNode();
        if (event.getId() != null) {
            root.put("eventId", event.getId());
        } else {
            root.putNull("eventId");
        }
        root.put("schemaVersion", OUTBOX_ENVELOPE_SCHEMA_VERSION);
        root.put("aggregateType", event.getAggregateType());
        root.put("aggregateId", event.getAggregateId());
        root.put("eventType", event.getEventType());
        root.put("topic", topic);
        root.set("payload", parsePayload(event));

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event envelope", e);
        }
    }

    private JsonNode parsePayload(OutboxEvent event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse outbox event payload", e);
        }
    }
}
