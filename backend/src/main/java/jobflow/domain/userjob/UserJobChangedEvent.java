package jobflow.domain.userjob;

public record UserJobChangedEvent(
        Long userId,
        Long jobId,
        UserJobStatus status
) {
}
