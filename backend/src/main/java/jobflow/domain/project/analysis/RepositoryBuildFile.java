package jobflow.domain.project.analysis;

public record RepositoryBuildFile(
        String path,
        BuildFileType type,
        String content
) {

    public static RepositoryBuildFile fromPath(String path, String content) {
        return new RepositoryBuildFile(path, BuildFileType.fromPath(path), content);
    }

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
