package jobflow.domain.project.analysis;

import java.util.List;

public record RepositoryAnalysisStats(
        Integer commitCount,
        Integer fileCount,
        Integer contributorCount,
        List<DirectoryStat> directories
) {

    public RepositoryAnalysisStats {
        directories = directories == null ? List.of() : List.copyOf(directories);
    }

    public static RepositoryAnalysisStats empty() {
        return new RepositoryAnalysisStats(null, null, null, List.of());
    }

    public record DirectoryStat(
            String path,
            int fileCount,
            int share
    ) {
    }
}
