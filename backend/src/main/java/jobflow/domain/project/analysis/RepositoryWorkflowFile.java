package jobflow.domain.project.analysis;

public record RepositoryWorkflowFile(
        String path,
        WorkflowFileType type,
        String content
) {

    public static RepositoryWorkflowFile fromPath(String path, String content) {
        return new RepositoryWorkflowFile(
                path,
                WorkflowFileType.fromPath(path),
                content == null ? "" : content
        );
    }

    public boolean isSupported() {
        return type != WorkflowFileType.UNKNOWN;
    }
}
