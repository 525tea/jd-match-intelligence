package jobflow.domain.outbox;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class KafkaDlqRetryService {

    static final int SUPPORTED_DLQ_ENVELOPE_SCHEMA_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final long sendTimeoutMillis;

    public KafkaDlqRetryService(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${jobflow.kafka.consumer.dlq.retry-send-timeout-millis:3000}") long sendTimeoutMillis
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    public DlqRetryResponse retry(DlqRetryRequest request) {
        validate(request);

        String targetTopic = request.sourceTopic().trim();
        String targetKey = normalizeKey(request.sourceKey());
        String payload = serializePayload(request.original().payload());

        try {
            kafkaTemplate.send(targetTopic, targetKey, payload).get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.KAFKA_DLQ_RETRY_FAILED, "DLQ 메시지 재처리가 중단되었습니다.");
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCode.KAFKA_DLQ_RETRY_FAILED, "DLQ 메시지 재처리 Kafka 발행 시간이 초과되었습니다.");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.KAFKA_DLQ_RETRY_FAILED, "DLQ 메시지를 Kafka로 재발행하지 못했습니다.");
        }

        log.warn(
                "Kafka DLQ message retried. targetTopic={}, targetKey={}, schemaVersion={}",
                targetTopic,
                targetKey,
                request.schemaVersion()
        );

        return new DlqRetryResponse(
                SUPPORTED_DLQ_ENVELOPE_SCHEMA_VERSION,
                targetTopic,
                targetKey
        );
    }

    private void validate(DlqRetryRequest request) {
        if (request == null) {
            throw invalidEnvelope("DLQ envelope body가 필요합니다.");
        }
        if (request.schemaVersion() == null
                || request.schemaVersion() != SUPPORTED_DLQ_ENVELOPE_SCHEMA_VERSION) {
            throw invalidEnvelope("지원하지 않는 DLQ envelope schemaVersion입니다.");
        }
        if (!StringUtils.hasText(request.sourceTopic())) {
            throw invalidEnvelope("DLQ envelope sourceTopic이 필요합니다.");
        }
        if (request.sourceTopic().trim().endsWith(".dlq")) {
            throw invalidEnvelope("sourceTopic은 DLQ topic이 아니라 원본 topic이어야 합니다.");
        }
        if (request.original() == null || isMissingPayload(request.original().payload())) {
            throw invalidEnvelope("DLQ envelope original.payload가 필요합니다.");
        }
    }

    private boolean isMissingPayload(JsonNode payload) {
        return payload == null || payload.isMissingNode() || payload.isNull();
    }

    private String normalizeKey(String sourceKey) {
        if (!StringUtils.hasText(sourceKey)) {
            return null;
        }
        return sourceKey;
    }

    private String serializePayload(JsonNode payload) {
        try {
            if (payload.isTextual()) {
                return payload.asText();
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE, "DLQ original.payload 직렬화에 실패했습니다.");
        }
    }

    private BusinessException invalidEnvelope(String message) {
        return new BusinessException(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE, message);
    }
}
