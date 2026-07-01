package jobflow.domain.outbox;

import java.time.LocalDateTime;

public record DlqMessageResponse(
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
        LocalDateTime retriedAt
) {

    public static DlqMessageResponse from(DlqMessage message) {
        return new DlqMessageResponse(
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
                message.getRetriedAt()
        );
    }
}
