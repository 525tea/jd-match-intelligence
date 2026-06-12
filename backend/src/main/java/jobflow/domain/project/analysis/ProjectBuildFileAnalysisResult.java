package jobflow.domain.project.analysis;

import java.util.List;

public record ProjectBuildFileAnalysisResult(
        RepositoryRef repositoryRef,
        int requestedFileCount,
        int foundFileCount,
        List<String> analyzedPaths,
        List<BuildFileSkillCandidate> skillCandidates
) {

    public ProjectBuildFileAnalysisResult {
        analyzedPaths = List.copyOf(analyzedPaths);
        skillCandidates = List.copyOf(skillCandidates);
    }

    public static ProjectBuildFileAnalysisResult empty(
            RepositoryRef repositoryRef,
            int requestedFileCount
    ) {
        return new ProjectBuildFileAnalysisResult(
                repositoryRef,
                requestedFileCount,
                0,
                List.of(),
                List.of()
        );
    }
}
