package jobflow.domain.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
