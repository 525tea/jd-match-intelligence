package jobflow.domain.project.analysis;

import java.util.Locale;

public enum WorkflowFileType {

    GITHUB_ACTIONS,
    UNKNOWN;

    public static WorkflowFileType fromPath(String path) {
        if (path == null || path.isBlank()) {
            return UNKNOWN;
        }

        String normalizedPath = path.toLowerCase(Locale.ROOT);
        String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);

        if (!normalizedPath.startsWith(".github/workflows/")
                && !normalizedPath.contains("/.github/workflows/")) {
            return UNKNOWN;
        }
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            return GITHUB_ACTIONS;
        }

        return UNKNOWN;
    }
}
