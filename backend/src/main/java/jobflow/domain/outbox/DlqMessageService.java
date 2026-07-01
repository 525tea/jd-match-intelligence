package jobflow.domain.outbox;

import java.time.OffsetDateTime;
import java.util.List;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqMessageService {

    private final DlqMessageRepository dlqMessageRepository;
    private final KafkaDlqRetryService kafkaDlqRetryService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveIfAbsent(String envelope) {
        DlqEnvelopeMetadata metadata = parseMetadata(envelope);
        if (dlqMessageRepository.findBySourceTopicAndSourcePartitionAndSourceOffset(
                metadata.sourceTopic(),
                metadata.sourcePartition(),
                metadata.sourceOffset()
        ).isPresent()) {
            return;
        }

        try {
            dlqMessageRepository.save(DlqMessage.create(
                    metadata.schemaVersion(),
                    metadata.sourceTopic(),
                    metadata.sourcePartition(),
                    metadata.sourceOffset(),
                    metadata.sourceKey(),
                    envelope,
                    metadata.failedAt()
            ));
        } catch (DataIntegrityViolationException e) {
            log.info(
                    "Duplicate DLQ message skipped. sourceTopic={}, partition={}, offset={}",
                    metadata.sourceTopic(),
                    metadata.sourcePartition(),
                    metadata.sourceOffset()
            );
        }
    }

    @Transactional(readOnly = true)
    public List<DlqMessageResponse> findMessages() {
        return dlqMessageRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .map(DlqMessageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DlqMessageDetailResponse getMessage(Long id) {
        DlqMessage message = findById(id);
        return DlqMessageDetailResponse.from(message, readEnvelope(message.getEnvelope()));
    }

    @Transactional
    public DlqRetryResponse retry(Long id) {
        DlqMessage message = findById(id);
        if (message.getStatus() == DlqMessageStatus.RETRIED) {
            throw new BusinessException(ErrorCode.KAFKA_DLQ_MESSAGE_STATUS_CONFLICT, "이미 재처리된 DLQ 메시지입니다.");
        }

        try {
            DlqRetryResponse response = kafkaDlqRetryService.retry(toRetryRequest(message.getEnvelope()));
            message.markRetried();
            return response;
        } catch (BusinessException e) {
            message.markRetryFailed(e.getMessage());
            throw e;
        } catch (Exception e) {
            message.markRetryFailed(e.getMessage());
            throw new BusinessException(ErrorCode.KAFKA_DLQ_RETRY_FAILED, "DLQ 메시지 재처리에 실패했습니다.");
        }
    }

    private DlqMessage findById(Long id) {
        return dlqMessageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KAFKA_DLQ_MESSAGE_NOT_FOUND, "DLQ 메시지를 찾을 수 없습니다."));
    }

    private DlqRetryRequest toRetryRequest(String envelope) {
        try {
            return objectMapper.readValue(envelope, DlqRetryRequest.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE, "저장된 DLQ envelope를 읽을 수 없습니다.");
        }
    }

    private JsonNode readEnvelope(String envelope) {
        try {
            return objectMapper.readTree(envelope);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE, "저장된 DLQ envelope를 읽을 수 없습니다.");
        }
    }

    private DlqEnvelopeMetadata parseMetadata(String envelope) {
        JsonNode root = readEnvelope(envelope);
        return new DlqEnvelopeMetadata(
                requiredInt(root, "schemaVersion"),
                requiredText(root, "sourceTopic"),
                requiredInt(root, "sourcePartition"),
                requiredLong(root, "sourceOffset"),
                optionalText(root, "sourceKey"),
                optionalOffsetDateTime(root, "failedAt")
        );
    }

    private int requiredInt(JsonNode root, String fieldName) {
        JsonNode value = root.path(fieldName);
        if (value.isMissingNode() || value.isNull() || !value.canConvertToInt()) {
            throw new BusinessException(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE, "DLQ envelope " + fieldName + " 값이 올바르지 않습니다.");
        }
        return value.intValue();
    }

    private long requiredLong(JsonNode root, String fieldName) {
        JsonNode value = root.path(fieldName);
        if (value.isMissingNode() || value.isNull() || !value.canConvertToLong()) {
            throw new BusinessException(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE, "DLQ envelope " + fieldName + " 값이 올바르지 않습니다.");
        }
        return value.longValue();
    }

    private String requiredText(JsonNode root, String fieldName) {
        String value = optionalText(root, fieldName);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE, "DLQ envelope " + fieldName + " 값이 필요합니다.");
        }
        return value;
    }

    private String optionalText(JsonNode root, String fieldName) {
        JsonNode value = root.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private OffsetDateTime optionalOffsetDateTime(JsonNode root, String fieldName) {
        String value = optionalText(root, fieldName);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private record DlqEnvelopeMetadata(
            int schemaVersion,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            String sourceKey,
            OffsetDateTime failedAt
    ) {
    }
}
