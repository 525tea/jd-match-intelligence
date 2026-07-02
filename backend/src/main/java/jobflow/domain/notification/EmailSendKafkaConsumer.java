package jobflow.domain.notification;

import jobflow.domain.outbox.KafkaConsumerIdempotencyService;
import jobflow.domain.outbox.OutboxKafkaEnvelope;
import jobflow.domain.outbox.OutboxKafkaMessageParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jobflow.kafka.consumer.enabled", havingValue = "true")
public class EmailSendKafkaConsumer {

    private final OutboxKafkaMessageParser messageParser;
    private final KafkaConsumerIdempotencyService idempotencyService;
    private final EmailSender emailSender;

    @KafkaListener(
            topics = "${jobflow.kafka.consumer.topics.email-send:email.send}",
            groupId = "${jobflow.kafka.consumer.group-id:jobflow-backend}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        OutboxKafkaEnvelope envelope = messageParser.parseEnvelope(record.value(), record.headers());
        handle(envelope);
    }

    void consume(String message) {
        OutboxKafkaEnvelope envelope = messageParser.parseEnvelope(message);
        handle(envelope);
    }

    private void handle(OutboxKafkaEnvelope envelope) {
        idempotencyService.runOnce("email-send", envelope.eventId(), () -> send(envelope));
    }

    private void send(OutboxKafkaEnvelope envelope) {
        JsonNode payload = envelope.payload();
        EmailSendRequest request = new EmailSendRequest(
                requiredText(payload, "to"),
                requiredText(payload, "subject"),
                requiredText(payload, "text"),
                optionalText(payload, "html")
        );

        EmailSendResult result = emailSender.send(request);
        if (!result.success()) {
            throw new IllegalStateException("Kafka email send failed. provider=%s, reason=%s"
                    .formatted(result.provider(), result.failureReason()));
        }

        log.info(
                "Kafka email send event handled. eventId={}, provider={}, providerMessageId={}, kafka_consumer_smoke_run_id={}",
                envelope.eventId(),
                result.provider(),
                result.providerMessageId(),
                optionalText(payload, "smokeRunId")
        );
    }

    private String requiredText(JsonNode payload, String fieldName) {
        String value = optionalText(payload, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Kafka email send message does not contain " + fieldName);
        }
        return value;
    }

    private String optionalText(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
