package jobflow.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "dlq_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dlq_messages_source",
                columnNames = {"source_topic", "source_partition", "source_offset"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int schemaVersion;

    @Column(nullable = false)
    private String sourceTopic;

    @Column(nullable = false)
    private int sourcePartition;

    @Column(nullable = false)
    private long sourceOffset;

    private String sourceKey;

    @Lob
    @Column(nullable = false, columnDefinition = "json")
    private String envelope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DlqMessageStatus status = DlqMessageStatus.PENDING;

    @Column(nullable = false)
    private int retryCount;

    @Column(columnDefinition = "text")
    private String lastError;

    private LocalDateTime failedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime retriedAt;

    public static DlqMessage create(
            int schemaVersion,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            String sourceKey,
            String envelope,
            OffsetDateTime failedAt
    ) {
        DlqMessage dlqMessage = new DlqMessage();
        dlqMessage.schemaVersion = schemaVersion;
        dlqMessage.sourceTopic = sourceTopic;
        dlqMessage.sourcePartition = sourcePartition;
        dlqMessage.sourceOffset = sourceOffset;
        dlqMessage.sourceKey = sourceKey;
        dlqMessage.envelope = envelope;
        dlqMessage.status = DlqMessageStatus.PENDING;
        dlqMessage.retryCount = 0;
        dlqMessage.failedAt = failedAt == null ? null : failedAt.toLocalDateTime();
        return dlqMessage;
    }

    public void markRetried() {
        this.status = DlqMessageStatus.RETRIED;
        this.retryCount++;
        this.lastError = null;
        this.retriedAt = LocalDateTime.now();
    }

    public void markRetryFailed(String lastError) {
        this.status = DlqMessageStatus.RETRY_FAILED;
        this.retryCount++;
        this.lastError = lastError;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
