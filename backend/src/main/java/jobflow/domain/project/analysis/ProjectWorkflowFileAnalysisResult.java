package jobflow.domain.project.analysis;

import java.util.List;

public record ProjectWorkflowFileAnalysisResult(
        RepositoryRef repositoryRef,
        int requestedFileCount,
        int foundFileCount,
        List<String> analyzedPaths,
        List<WorkflowExperienceTagCandidate> experienceTagCandidates
) {

    public ProjectWorkflowFileAnalysisResult {
        analyzedPaths = List.copyOf(analyzedPaths);
        experienceTagCandidates = List.copyOf(experienceTagCandidates);
    }

    public static ProjectWorkflowFileAnalysisResult empty(
            RepositoryRef repositoryRef,
            int requestedFileCount
    ) {
        return new ProjectWorkflowFileAnalysisResult(
                repositoryRef,
                requestedFileCount,
                0,
                List.of(),
                List.of()
        );
    }
}
