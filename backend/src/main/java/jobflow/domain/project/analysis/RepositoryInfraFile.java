package jobflow.domain.project.analysis;

public record RepositoryInfraFile(
        String path,
        InfraFileType type,
        String content
) {

    public static RepositoryInfraFile fromPath(String path, String content) {
        return new RepositoryInfraFile(
                path,
                InfraFileType.fromPath(path),
                content == null ? "" : content
        );
    }
}
