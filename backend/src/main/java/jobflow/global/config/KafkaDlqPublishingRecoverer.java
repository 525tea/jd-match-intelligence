package jobflow.global.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jobflow.domain.outbox.DlqMessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
public class KafkaDlqPublishingRecoverer implements ConsumerRecordRecoverer {

    static final int DLQ_ENVELOPE_SCHEMA_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DlqMessageService dlqMessageService;
    private final Clock clock;
    private final String topicSuffix;
    private final long sendTimeoutMillis;

    public KafkaDlqPublishingRecoverer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            DlqMessageService dlqMessageService,
            Clock clock,
            String topicSuffix,
            long sendTimeoutMillis
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.dlqMessageService = dlqMessageService;
        this.clock = clock;
        this.topicSuffix = StringUtils.hasText(topicSuffix) ? topicSuffix : ".dlq";
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        String dlqTopic = record.topic() + topicSuffix;
        String key = record.key() == null ? null : String.valueOf(record.key());
        String message = buildDlqMessage(record, exception);
        dlqMessageService.saveIfAbsent(message);

        try {
            kafkaTemplate.send(dlqTopic, key, message).get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing Kafka DLQ message", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out while publishing Kafka DLQ message", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish Kafka DLQ message", e);
        }

        log.warn(
                "Kafka message published to DLQ. sourceTopic={}, dlqTopic={}, partition={}, offset={}, key={}, error={}",
                record.topic(),
                dlqTopic,
                record.partition(),
                record.offset(),
                key,
                exception.getClass().getSimpleName()
        );
    }

    private String buildDlqMessage(ConsumerRecord<?, ?> record, Exception exception) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", DLQ_ENVELOPE_SCHEMA_VERSION);
        root.put("sourceTopic", record.topic());
        root.put("sourcePartition", record.partition());
        root.put("sourceOffset", record.offset());
        if (record.key() == null) {
            root.putNull("sourceKey");
        } else {
            root.put("sourceKey", String.valueOf(record.key()));
        }
        root.put("failedAt", OffsetDateTime.now(clock).toString());
        root.put("deliveryAttempt", deliveryAttempt(record));

        ObjectNode error = root.putObject("error");
        error.put("className", exception.getClass().getName());
        error.put("message", toErrorMessage(exception));

        ObjectNode original = root.putObject("original");
        original.set("payload", originalPayload(record));

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Kafka DLQ envelope", e);
        }
    }

    private JsonNode originalPayload(ConsumerRecord<?, ?> record) {
        if (record.value() == null) {
            return objectMapper.nullNode();
        }

        String value = String.valueOf(record.value());
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }

    private int deliveryAttempt(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader("kafka_deliveryAttempt");
        if (header == null) {
            return 0;
        }
        byte[] headerValue = header.value();
        if (headerValue.length == Integer.BYTES) {
            return ByteBuffer.wrap(headerValue).getInt();
        }
        String value = new String(headerValue, StandardCharsets.UTF_8);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String toErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
