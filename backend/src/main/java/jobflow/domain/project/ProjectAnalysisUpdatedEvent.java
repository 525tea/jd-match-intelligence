package jobflow.domain.project;

public record ProjectAnalysisUpdatedEvent(
        Long userId,
        Long userProjectId
) {
}
