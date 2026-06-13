package jobflow.domain.project.analysis;

import java.util.List;

public record ProjectInfraFileAnalysisResult(
        RepositoryRef repositoryRef,
        int requestedFileCount,
        int foundFileCount,
        List<String> analyzedPaths,
        List<InfraExperienceTagCandidate> experienceTagCandidates
) {

    public ProjectInfraFileAnalysisResult {
        analyzedPaths = List.copyOf(analyzedPaths);
        experienceTagCandidates = List.copyOf(experienceTagCandidates);
    }

    public static ProjectInfraFileAnalysisResult empty(
            RepositoryRef repositoryRef,
            int requestedFileCount
    ) {
        return new ProjectInfraFileAnalysisResult(
                repositoryRef,
                requestedFileCount,
                0,
                List.of(),
                List.of()
        );
    }
}
