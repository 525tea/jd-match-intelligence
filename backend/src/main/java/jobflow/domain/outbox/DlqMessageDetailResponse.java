package jobflow.domain.outbox;

import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

public record DlqMessageDetailResponse(
        Long id,
        int schemaVersion,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String sourceKey,
        DlqMessageStatus status,
        int retryCount,
        String lastError,
        LocalDateTime failedAt,
        LocalDateTime createdAt,
        LocalDateTime retriedAt,
        JsonNode envelope
) {

    public static DlqMessageDetailResponse from(DlqMessage message, JsonNode envelope) {
        return new DlqMessageDetailResponse(
                message.getId(),
                message.getSchemaVersion(),
                message.getSourceTopic(),
                message.getSourcePartition(),
                message.getSourceOffset(),
                message.getSourceKey(),
                message.getStatus(),
                message.getRetryCount(),
                message.getLastError(),
                message.getFailedAt(),
                message.getCreatedAt(),
                message.getRetriedAt(),
                envelope
        );
    }
}
