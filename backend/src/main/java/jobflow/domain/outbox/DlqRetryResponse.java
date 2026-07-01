package jobflow.domain.outbox;

public record DlqRetryResponse(
        int schemaVersion,
        String targetTopic,
        String targetKey
) {
}
