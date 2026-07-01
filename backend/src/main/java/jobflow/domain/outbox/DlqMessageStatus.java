package jobflow.domain.outbox;

public enum DlqMessageStatus {
    PENDING,
    RETRIED,
    RETRY_FAILED
}
