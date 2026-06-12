package jobflow.domain.project.analysis;

public record RepositoryFile(
        String path,
        String content
) {

    public RepositoryFile {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
    }

    public RepositoryBuildFile toBuildFile() {
        return RepositoryBuildFile.fromPath(path, content);
    }
}
